package com.zhyq.park.dashboard;

import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.dashboard.controller.DashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardControllerIncomeSourcesTest {

    @Test
    void overviewSeparatesAuthoritativeRentAndVendingIncome() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
        when(jdbc.queryForObject(contains("fee_type IN ('租金','物业费')"), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("120000.00"), new BigDecimal("100000.00"));
        when(jdbc.queryForObject(contains("ops_vending_sale"), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("3500.50"));

        // BizSettings 走同一个 mock jdbc:查不到配置就回默认值,不影响本用例关心的收入口径
        Map<String, Object> overview = new DashboardController(jdbc, new BizSettings(jdbc)).overview().getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) overview.get("incomeSources");

        assertEquals(new BigDecimal("120000.00"), sources.get("rentPropertyBilled"));
        assertEquals(new BigDecimal("100000.00"), sources.get("rentPropertyReceived"));
        assertEquals(new BigDecimal("3500.50"), sources.get("vendingSales"));
    }
}
