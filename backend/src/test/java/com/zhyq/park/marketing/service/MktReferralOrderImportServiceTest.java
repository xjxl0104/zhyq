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
    @Mock com.zhyq.park.marketing.mapper.MktWarehouseMapper warehouseMapper;

    MktReferralOrderImportService service;

    @BeforeAll
    static void initMp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktReferralOrder.class);
        TableInfoHelper.initTableInfo(a, MktServiceContract.class);
        TableInfoHelper.initTableInfo(a, Customer.class);
        TableInfoHelper.initTableInfo(a, MktCustomerGrade.class);
        TableInfoHelper.initTableInfo(a, com.zhyq.park.marketing.entity.MktWarehouse.class);
    }

    @BeforeEach
    void setUp() {
        service = new MktReferralOrderImportService(customerMapper, contractMapper, gradeMapper, orderMapper,
                commissionService, bizSettings, new ObjectMapper(), warehouseMapper, new MktContractTermsService(org.mockito.Mockito.mock(com.zhyq.park.marketing.mapper.MktServiceContractVersionMapper.class)));
        var warehouse = new com.zhyq.park.marketing.entity.MktWarehouse(); warehouse.setId(7L); warehouse.setJoinStatus(5); warehouse.setFeeModel("{\"perOrder\":1,\"perItem\":0}");
        lenient().when(warehouseMapper.selectById(7L)).thenReturn(warehouse);
    }

    @Test
    void rejectsNegativeMarginAndMissingCommissionRate() {
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(customer(5L,99L,"A"));
        var contract=new MktServiceContract();contract.setId(20L);contract.setSignMode(1);contract.setWarehouseId(7L);contract.setGrade("A");contract.setPriceTable("{\"perOrder\":1}");
        when(contractMapper.selectOne(any(Wrapper.class))).thenReturn(contract);
        assertThatThrownBy(()->service.toEvent(row("RATE"),null,7)).isInstanceOf(BizException.class).hasMessageContaining("比例配置");
        var grade=new MktCustomerGrade();grade.setErpTotalRate(new BigDecimal("5"));when(gradeMapper.selectOne(any(Wrapper.class))).thenReturn(grade);
        assertThatThrownBy(()->service.toEvent(row("LOSS"),null,7)).isInstanceOf(BizException.class).hasMessageContaining("毛利为负");
        verify(commissionService,never()).createAndSplit(any());
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
        k.setId(20L); k.setSignMode(1); k.setGrade("B"); k.setWarehouseId(7L); k.setPriceTable("{\"perOrder\":10,\"perItem\":0.5}");
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
        assertThat(ev.qty()).isEqualTo(5); assertThat(ev.packages()).isEqualTo(1); assertThat(ev.logisticsNo()).isEqualTo("SF1");
    }

    @Test
    void customerWithoutReferrerIsRejected() {
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(customer(5L, null, "A"));
        MktServiceContract c = new MktServiceContract(); c.setId(20L); c.setWarehouseId(7L); c.setSignMode(1);
        when(contractMapper.selectOne(any())).thenReturn(c);
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
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(existingOrder(), null);
        // 第 2 行客户不存在 → 错误;第 3 行正常
        when(customerMapper.selectOne(any(Wrapper.class))).thenReturn(customer(5L, 99L, "A"), null, customer(5L, 99L, "A"));
        MktServiceContract k = new MktServiceContract(); k.setId(20L); k.setSignMode(1); k.setWarehouseId(7L); k.setGrade("A"); k.setPriceTable("{\"perOrder\":10}");
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

    @Test void negativeQuantityAndFutureShippingAreRejected() {
        assertThatThrownBy(() -> service.toEvent(new OutboundRow("X","13800000001",-1,1,LocalDateTime.now(),null,null,null),null,7))
                .isInstanceOf(BizException.class).hasMessageContaining("件数");
        assertThatThrownBy(() -> service.toEvent(new OutboundRow("X","13800000001",1,1,LocalDateTime.now().plusDays(1),null,null,null),null,7))
                .isInstanceOf(BizException.class).hasMessageContaining("发货时间");
    }
    @Test void refusesDirectSignTurnoverAsParkRevenue() {
        when(customerMapper.selectOne(any())).thenReturn(customer(5L,99L,"A"));
        MktServiceContract c=new MktServiceContract();c.setWarehouseId(7L);c.setSignMode(2);
        when(contractMapper.selectOne(any())).thenReturn(c);
        assertThatThrownBy(() -> service.toEvent(row("DIRECT"),null,7)).isInstanceOf(BizException.class).hasMessageContaining("直签");
    }
    @Test void directShipmentsWithoutPartnerAreStoredWithoutAnyFinancialEvent() {
        when(customerMapper.selectOne(any())).thenReturn(customer(5L,null,null));
        MktServiceContract contract = directContract(); contract.setProjectId(3L);
        // Operating imports do not need park cost rates or a commission grade.
        contract.setPriceTable("{\"monthly\":20000}");
        when(contractMapper.selectOne(any())).thenReturn(contract);
        var warehouse = new com.zhyq.park.marketing.entity.MktWarehouse(); warehouse.setId(7L); warehouse.setJoinStatus(5);
        when(warehouseMapper.selectById(7L)).thenReturn(warehouse);
        LocalDateTime shipped = LocalDateTime.now().minusDays(1);
        OutboundRow actual = new OutboundRow("DIRECT-OPS", "13800000001", 12, 3, shipped, "SF-DIRECT", null, new BigDecimal("800.50"));

        ImportResult result = service.importRows(List.of(actual), 3L);

        assertThat(result.imported()).isEqualTo(1); assertThat(result.skipped()).isZero(); assertThat(result.errors()).isEmpty();
        ArgumentCaptor<MktReferralOrder> order = ArgumentCaptor.forClass(MktReferralOrder.class);
        verify(orderMapper).insert(order.capture());
        MktReferralOrder stored = order.getValue();
        assertThat(stored.getSourceType()).isEqualTo(MktCommissionService.SOURCE_OUTBOUND);
        assertThat(stored.getSourceId()).isEqualTo(20L); assertThat(stored.getCustomerId()).isEqualTo(5L);
        assertThat(stored.getWarehouseId()).isEqualTo(7L); assertThat(stored.getProjectId()).isEqualTo(3L);
        assertThat(stored.getPromoterId()).isNull();
        assertThat(stored.getServiceFee()).isZero(); assertThat(stored.getBaseAmount()).isZero();
        assertThat(stored.getPoolAmount()).isZero(); assertThat(stored.getPoolFactor()).isZero();
        assertThat(stored.getQty()).isEqualTo(12); assertThat(stored.getPackages()).isEqualTo(3);
        assertThat(stored.getGoodsAmount()).isEqualByComparingTo("800.50"); assertThat(stored.getLogisticsNo()).isEqualTo("SF-DIRECT");
        assertThat(stored.getEventTime()).isEqualTo(shipped); assertThat(stored.getStatus()).isEqualTo(2);
        assertThat(stored.getRemark()).contains("不计佣", "不产生园区应收");
        org.mockito.Mockito.verifyNoInteractions(commissionService,gradeMapper);
    }

    @Test void directImportRejectsInactiveWarehouseAndOutOfContractDates() {
        when(customerMapper.selectOne(any())).thenReturn(customer(5L,null,null));
        MktServiceContract contract = directContract(); contract.setStartDate(java.time.LocalDate.now().plusDays(1));
        when(contractMapper.selectOne(any())).thenReturn(contract);
        ImportResult dates = service.importRows(List.of(row("BEFORE-CONTRACT")), null);
        assertThat(dates.errors()).singleElement().asString().contains("生效日期");
        contract.setStartDate(null);
        var suspended = new com.zhyq.park.marketing.entity.MktWarehouse(); suspended.setJoinStatus(6);
        when(warehouseMapper.selectById(7L)).thenReturn(suspended);
        ImportResult status = service.importRows(List.of(row("SUSPENDED")), null);
        assertThat(status.errors()).singleElement().asString().contains("未上线");
        verify(orderMapper,never()).insert(any(MktReferralOrder.class));
        org.mockito.Mockito.verifyNoInteractions(commissionService,gradeMapper);
    }

    @Test void directImportDoesNotBypassRowOrProjectValidation() {
        when(customerMapper.selectOne(any())).thenReturn(customer(5L,null,null));
        MktServiceContract contract = directContract(); contract.setProjectId(3L);
        when(contractMapper.selectOne(any())).thenReturn(contract);
        ImportResult invalid = service.importRows(List.of(new OutboundRow("BAD-QTY","13800000001",-1,1,LocalDateTime.now(),null,null,null), row("OTHER-PROJECT")), 99L);
        assertThat(invalid.imported()).isZero(); assertThat(invalid.errors()).hasSize(2);
        assertThat(invalid.errors().get(0)).contains("件数"); assertThat(invalid.errors().get(1)).contains("当前园区");
        verify(orderMapper,never()).insert(any(MktReferralOrder.class));
        org.mockito.Mockito.verifyNoInteractions(commissionService,gradeMapper);
    }

    @Test void concurrentDirectDuplicateIsSkippedWithoutCommission() {
        when(orderMapper.selectOne(any())).thenReturn(null,existingOrder());
        when(customerMapper.selectOne(any())).thenReturn(customer(5L,null,null));
        when(contractMapper.selectOne(any())).thenReturn(directContract());
        when(orderMapper.insert(any(MktReferralOrder.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("uk_referral_order_source"));
        ImportResult result = service.importRows(List.of(row("CONCURRENT")), null);
        assertThat(result.imported()).isZero(); assertThat(result.skipped()).isEqualTo(1); assertThat(result.errors()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(commissionService,gradeMapper);
    }

    @Test void directAndParkRowsUseSeparateImportPathsInOneWorkbook() {
        when(customerMapper.selectOne(any())).thenReturn(customer(5L,99L,"A"));
        MktServiceContract direct = directContract();
        MktServiceContract park = directContract(); park.setId(21L); park.setSignMode(1); park.setPriceTable("{\"perOrder\":10}");
        when(contractMapper.selectOne(any())).thenReturn(direct,park);
        MktCustomerGrade grade = new MktCustomerGrade(); grade.setCode("A"); grade.setErpTotalRate(new BigDecimal("5"));
        when(gradeMapper.selectOne(any())).thenReturn(grade);
        ImportResult result = service.importRows(List.of(row("DIRECT-MIX"),row("PARK-MIX")),null);
        assertThat(result.imported()).isEqualTo(2); assertThat(result.errors()).isEmpty();
        verify(orderMapper,times(1)).insert(any(MktReferralOrder.class));
        ArgumentCaptor<CommissionEvent> event=ArgumentCaptor.forClass(CommissionEvent.class);
        verify(commissionService,times(1)).createAndSplit(event.capture());
        assertThat(event.getValue().sourceNo()).isEqualTo("PARK-MIX");
        assertThat(event.getValue().poolAmount()).isEqualByComparingTo("0.50");
    }

    private static MktServiceContract directContract() {
        MktServiceContract c = new MktServiceContract(); c.setId(20L); c.setWarehouseId(7L); c.setCustomerId(5L); c.setStatus(4); c.setSignMode(2); return c;
    }

    @Test void duplicateLookupIsScopedToWarehouseAndRejectsConflictingOwnership() {
        when(customerMapper.selectOne(any())).thenReturn(customer(5L,null,null));
        when(contractMapper.selectOne(any())).thenReturn(directContract());
        MktReferralOrder conflicting = existingOrder(); conflicting.setCustomerId(9L);
        when(orderMapper.selectOne(any())).thenReturn(conflicting);
        ImportResult result = service.importRows(List.of(row("SAME-NUMBER")),null);
        assertThat(result.imported()).isZero(); assertThat(result.skipped()).isZero();
        assertThat(result.errors()).singleElement().asString().contains("其他客户或合同");
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MktReferralOrder>> query = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(orderMapper).selectOne(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("warehouse_id");
        assertThat(query.getValue().getParamNameValuePairs()).containsValue(7L);
        verify(orderMapper,never()).insert(any(MktReferralOrder.class));
    }

    private static MktReferralOrder existingOrder() {
        var order = new MktReferralOrder(); order.setId(55L); order.setWarehouseId(7L); order.setCustomerId(5L); order.setSourceId(20L); return order;
    }

    private static OutboundRow row(String no) {
        return new OutboundRow(no, "13800000001", 1, 1, LocalDateTime.now(), "SF", null, null);
    }

    private static Customer customer(Long id, Long referrer, String grade) {
        Customer c = new Customer(); c.setId(id); c.setName("客户" + id); c.setReferrerId(referrer); c.setGrade(grade);
        return c;
    }
}
