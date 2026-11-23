package com.zhyq.park.energy;

import com.zhyq.park.common.exception.GlobalExceptionHandler;
import com.zhyq.park.energy.controller.FeePeriodController;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.mapper.FeePeriodMapper;
import com.zhyq.park.energy.service.FeeNoticeExportService;
import com.zhyq.park.energy.service.FeeNoticePaymentService;
import com.zhyq.park.energy.service.FeeNoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeeNoticePaymentControllerTest {
    final FeeNoticePaymentService payments = mock(FeeNoticePaymentService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new FeePeriodController(mock(FeePeriodMapper.class),
                        mock(FeeNoticeService.class), mock(FeeNoticeExportService.class), payments))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest @ValueSource(strings = {"1.9", "1.0", "\"1\"", "true", "null", "[]", "{}"})
    void rejectsCoercedStatusTypesInsteadOfTruncatingThem(String jsonValue) throws Exception {
        mvc.perform(put("/energy/fee-period/notice/11/payment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentStatus\":" + jsonValue + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(payments);
    }

    @Test void returnsTheFullUpdatedNotice() throws Exception {
        FeeNotice n = new FeeNotice(); n.setId(11L); n.setPaymentStatus(1); n.setTenantName("测试租户");
        when(payments.updateStatus(11L, 1)).thenReturn(n);
        mvc.perform(put("/energy/fee-period/notice/11/payment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentStatus\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.paymentStatus").value(1))
                .andExpect(jsonPath("$.data.tenantName").value("测试租户"))
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
