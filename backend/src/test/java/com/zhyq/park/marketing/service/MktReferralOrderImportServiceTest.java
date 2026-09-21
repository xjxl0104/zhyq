package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerGrade;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktCustomerGradeMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.service.MktCommissionService.CommissionEvent;
import com.zhyq.park.marketing.service.MktReferralOrderImportService.ImportResult;
import com.zhyq.park.marketing.service.MktReferralOrderImportService.OutboundRow;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MktReferralOrderImportServiceTest {

    @Mock CustomerMapper customerMapper;
    @Mock MktServiceContractMapper contractMapper;
    @Mock MktCustomerGradeMapper gradeMapper;
    @Mock MktReferralOrderMapper orderMapper;
    @Mock MktCommissionService commissionService;
    @Mock BizSettings bizSettings;

    MktReferralOrderImportService service;

    @BeforeAll
    static void initMp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktReferralOrder.class);
        TableInfoHelper.initTableInfo(a, MktServiceContract.class);
        TableInfoHelper.initTableInfo(a, Customer.class);
        TableInfoHelper.initTableInfo(a, MktCustomerGrade.class);
    }

    @BeforeEach
    void setUp() {
        service = new MktReferralOrderImportService(customerMapper, contractMapper, gradeMapper, orderMapper,
                commissionService, bizSettings, new ObjectMapper());
    }

    @Test
    void serviceFeeIsPerOrderTimesPackagesPlusPerItemTimesQty() {
        // 单票 3 元 × 1 包裹 + 按件 0.5 × 5 件 = 5.50
        assertThat(service.serviceFee("{\"perOrder\":3,\"perItem\":0.5}", 5, 1)).isEqualByComparingTo("5.50");
        assertThat(service.serviceFee("{\"perOrder\":3}", 5, 2)).isEqualByComparingTo("6.00");
        assertThat(service.serviceFee(null, 5, 1)).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> service.serviceFee("not json", 1, 1)).isInstanceOf(BizException.class);
    }

    @Test
    void rowBecomesOutboundEventWithParkComputedFee() {
        Customer c = customer(5L, 99L, "A");
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(c);
        MktServiceContract k = new MktServiceContract();
        k.setId(20L); k.setGrade("B"); k.setWarehouseId(7L); k.setPriceTable("{\"perOrder\":10,\"perItem\":0.5}");
        when(contractMapper.selectOne(any(Wrapper.class))).thenReturn(k);
        MktCustomerGrade g = new MktCustomerGrade(); g.setCode("B"); g.setErpTotalRate(new BigDecimal("6"));
        when(gradeMapper.selectOne(any(Wrapper.class))).thenReturn(g);
        LocalDateTime shipped = LocalDateTime.of(2026, 9, 18, 10, 20);

        CommissionEvent ev = service.toEvent(new OutboundRow("OUT001", "13800000001", 5, 1, shipped, "SF1", null, null), 1L, 7);

        assertThat(ev.sourceType()).isEqualTo(MktCommissionService.SOURCE_OUTBOUND);
        assertThat(ev.sourceNo()).isEqualTo("OUT001");
        assertThat(ev.sellerPromoterId()).isEqualTo(99L);
        assertThat(ev.grade()).isEqualTo("B");                        // 合同评级快照优先于客户当前评级
        assertThat(ev.baseAmount()).isEqualByComparingTo("12.50");    // 10×1 + 0.5×5
        assertThat(ev.poolAmount()).isEqualByComparingTo("0.75");     // 12.5 × 6%
        assertThat(ev.unfreezeAt()).isEqualTo(shipped.plusDays(7));
        assertThat(ev.warehouseId()).isEqualTo(7L);
        assertThat(ev.sourceId()).isEqualTo(20L);
    }

    @Test
    void customerWithoutReferrerIsRejected() {
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(customer(5L, null, "A"));
        assertThatThrownBy(() -> service.toEvent(row("OUT001"), 1L, 7))
                .isInstanceOf(BizException.class).hasMessageContaining("无推荐伙伴");
    }

    @Test
    void customerWithoutActiveContractIsRejected() {
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(customer(5L, 99L, "A"));
        when(contractMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.toEvent(row("OUT001"), 1L, 7))
                .isInstanceOf(BizException.class).hasMessageContaining("服务合同");
    }

    @Test
    void importRowsSkipsDuplicatesAndCollectsErrorsWithoutAborting() {
        when(bizSettings.getInt(eq("marketing"), eq("freeze_days"), anyInt())).thenReturn(7);
        when(orderMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 0L, 0L);
        // 第 2 行客户不存在 → 错误;第 3 行正常
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(null, customer(5L, 99L, "A"));
        MktServiceContract k = new MktServiceContract(); k.setId(20L); k.setGrade("A"); k.setPriceTable("{\"perOrder\":10}");
        lenient().when(contractMapper.selectOne(any(Wrapper.class))).thenReturn(k);
        MktCustomerGrade g = new MktCustomerGrade(); g.setCode("A"); g.setErpTotalRate(new BigDecimal("8"));
        lenient().when(gradeMapper.selectOne(any(Wrapper.class))).thenReturn(g);

        ImportResult r = service.importRows(List.of(row("DUP"), row("BAD"), row("OK")), 1L);

        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.imported()).isEqualTo(1);
        assertThat(r.errors()).hasSize(1).first().asString().contains("BAD");
        verify(commissionService, times(1)).createAndSplit(any());
    }

    @Test
    void oversizeFileIsRejectedBeforeParsing() {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile f = new MockMultipartFile("file", "outbound.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", big);
        assertThatThrownBy(() -> service.importWorkbook(f, 1L))
                .isInstanceOf(BizException.class).hasMessageContaining("文件过大");
    }

    @Test
    void nonExcelExtensionIsRejected() {
        MockMultipartFile f = new MockMultipartFile("file", "outbound.csv", "text/csv", "no".getBytes());
        assertThatThrownBy(() -> service.importWorkbook(f, 1L))
                .isInstanceOf(BizException.class).hasMessageContaining(".xlsx / .xls");
    }

    @Test
    void extensionlessFileIsStillParsed() {
        // 没有扩展名时不拦,交给 POI 判断(只做大小/行数兜底)
        MockMultipartFile f = new MockMultipartFile("file", "outbound", "application/octet-stream", "not a workbook".getBytes());
        assertThatThrownBy(() -> service.importWorkbook(f, 1L))
                .isInstanceOf(BizException.class).hasMessageContaining("文件解析失败");
    }

    @Test
    void emptyPhoneIsRejected() {
        assertThatThrownBy(() -> service.toEvent(new OutboundRow("X", "", 1, 1, null, null, null, null), 1L, 7))
                .isInstanceOf(BizException.class);
        verify(customerMapper, never()).selectOne(any(Wrapper.class));
    }

    private static OutboundRow row(String no) {
        return new OutboundRow(no, "13800000001", 1, 1, LocalDateTime.now(), "SF", null, null);
    }

    private static Customer customer(Long id, Long referrer, String grade) {
        Customer c = new Customer(); c.setId(id); c.setName("客户" + id); c.setReferrerId(referrer); c.setGrade(grade);
        return c;
    }
}
