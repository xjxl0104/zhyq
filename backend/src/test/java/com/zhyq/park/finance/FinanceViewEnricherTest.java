package com.zhyq.park.finance;

import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.finance.service.FinanceViewEnricher;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 财务各页面的租客名/账单展示口径。
 *
 * <p>财务模块此前每个页面各显示各的:所有账单页显示租客名,逾期页显示裸的 tenantRefId
 * (界面上就是个"2"),流水/发票/收款通知只有"关联账单ID",收据连账单号都没有。
 * 这些用例把统一后的口径锁住。</p>
 */
class FinanceViewEnricherTest {

    private ReceivableRegisterMapper registerMapper;
    private BizTenantMapper tenantMapper;
    private BillMapper billMapper;
    private FinanceViewEnricher enricher;

    @BeforeEach
    void setUp() {
        registerMapper = mock(ReceivableRegisterMapper.class);
        tenantMapper = mock(BizTenantMapper.class);
        billMapper = mock(BillMapper.class);
        enricher = new FinanceViewEnricher(billMapper, registerMapper, tenantMapper);
    }

    private static ReceivableRegister register(long id, String tenantName, String agreementNo) {
        ReceivableRegister r = new ReceivableRegister();
        r.setId(id);
        r.setTenantNameRaw(tenantName);
        r.setAgreementNoRaw(agreementNo);
        return r;
    }

    private static BizTenant tenant(long id, String name) {
        BizTenant t = new BizTenant();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private static Bill bill(long id, Long registerId, Long tenantRefId) {
        Bill b = new Bill();
        b.setId(id);
        b.setCode("RR1V1R202601");
        b.setFeeType("租金");
        b.setReceivableRegisterId(registerId);
        b.setTenantRefId(tenantRefId);
        b.setAmount(new BigDecimal("124800.00"));
        return b;
    }

    @Test
    @DisplayName("租客名优先取应收登记明细 —— 登记明细是财务权威来源,账单金额也生成自它")
    void prefersRegisterTenantName() {
        when(registerMapper.selectBatchIds(any())).thenReturn(List.of(register(7L, "登记表里的租户", "XY-2026-001")));
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of(tenant(2L, "租客档案里的旧名字")));

        List<Bill> bills = new ArrayList<>(List.of(bill(1L, 7L, 2L)));
        enricher.enrichBills(bills);

        assertThat(bills.get(0).getTenantName()).isEqualTo("登记表里的租户");
        assertThat(bills.get(0).getAgreementNo()).isEqualTo("XY-2026-001");
    }

    @Test
    @DisplayName("没挂登记表的账单(如历史演示数据)回落到租客档案,不显示裸 id")
    void fallsBackToTenantProfile() {
        when(registerMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of(tenant(2L, "绿源环保股份公司")));

        List<Bill> bills = new ArrayList<>(List.of(bill(1L, null, 2L)));
        enricher.enrichBills(bills);

        assertThat(bills.get(0).getTenantName()).isEqualTo("绿源环保股份公司");
        // 没有登记表就没有协议编号,前端显示 "-"
        assertThat(bills.get(0).getAgreementNo()).isNull();
    }

    @Test
    @DisplayName("登记表的租户名是空白时同样回落,不能把空字符串当名字用")
    void blankRegisterNameFallsBack() {
        when(registerMapper.selectBatchIds(any())).thenReturn(List.of(register(7L, "   ", "XY-2026-001")));
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of(tenant(2L, "租客档案名")));

        List<Bill> bills = new ArrayList<>(List.of(bill(1L, 7L, 2L)));
        enricher.enrichBills(bills);

        assertThat(bills.get(0).getTenantName()).isEqualTo("租客档案名");
    }

    @Test
    @DisplayName("流水/收据/发票/通知按 billId 批量取展示信息,拿到的是同一套口径")
    void resolvesBillViewsForDownstreamPages() {
        when(billMapper.selectList(any())).thenReturn(List.of(bill(11L, 7L, 2L)));
        when(registerMapper.selectBatchIds(any())).thenReturn(List.of(register(7L, "登记表里的租户", "XY-2026-001")));
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of(tenant(2L, "旧名字")));

        // 故意混入 null 与重复 id:下游记录的 bill_id 可能为空(如手工补录的流水),
        // 传进来不该炸,也不该重复查库。List.of 不收 null,所以用 Arrays.asList
        Map<Long, FinanceViewEnricher.BillView> views =
                enricher.resolveBillViews(java.util.Arrays.asList(11L, null, 11L));

        assertThat(views).containsOnlyKeys(11L);
        FinanceViewEnricher.BillView v = views.get(11L);
        assertThat(v.tenantName()).isEqualTo("登记表里的租户");
        assertThat(v.billCode()).isEqualTo("RR1V1R202601");
        assertThat(v.feeType()).isEqualTo("租金");
    }

    @Test
    @DisplayName("空入参不查库、不炸")
    void handlesEmptyInput() {
        assertThat(enricher.resolveBillViews(List.of())).isEmpty();
        assertThat(enricher.resolveBillViews(null)).isEmpty();
        enricher.enrichBills(null);
        enricher.enrichBills(new ArrayList<>());
    }

    // ---------- 收银台租客选项 ----------
    // 收银台原先只列"还欠着钱的租客":当月有应收/有欠款的租客一旦口径没对上就整个消失,
    // 收银员无从下手。现在的口径:档案里的每个租客都能选,欠款的排前面并带欠款汇总。

    private static Bill owingBill(long id, Long registerId, Long tenantRefId,
                                  String amount, String paid) {
        Bill b = bill(id, registerId, tenantRefId);
        b.setDirection(1);
        b.setAmount(new BigDecimal(amount));
        b.setPaidAmount(new BigDecimal(paid));
        return b;
    }

    @Test
    @DisplayName("档案里的每个租客都出现在收银台选项里,没欠款的也能选")
    void cashierListsEveryTenant() {
        when(tenantMapper.selectList(any())).thenReturn(List.of(
                tenant(1L, "甲公司"), tenant(2L, "乙公司")));
        when(registerMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of(tenant(1L, "甲公司")));

        List<FinanceViewEnricher.TenantOption> options = enricher.cashierTenantOptions(null,
                List.of(owingBill(11L, null, 1L, "100.00", "40.00")));

        assertThat(options).hasSize(2);
        assertThat(options.get(0).tenantRefId()).isEqualTo(1L);
        assertThat(options.get(0).owe()).isEqualByComparingTo("60.00");
        assertThat(options.get(0).billCount()).isEqualTo(1);
        // 乙公司没有欠款,但同样可选 —— 欠款为 0、笔数为 0
        assertThat(options.get(1).tenantRefId()).isEqualTo(2L);
        assertThat(options.get(1).owe()).isEqualByComparingTo("0");
        assertThat(options.get(1).billCount()).isZero();
    }

    @Test
    @DisplayName("欠款多的排前面;没欠款的按名字排在最后")
    void cashierSortsOwingFirst() {
        when(tenantMapper.selectList(any())).thenReturn(List.of(
                tenant(1L, "A租户"), tenant(2L, "B租户"), tenant(3L, "C租户"), tenant(4L, "D租户")));
        when(registerMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of(
                tenant(2L, "B租户"), tenant(3L, "C租户")));

        List<FinanceViewEnricher.TenantOption> options = enricher.cashierTenantOptions(null, List.of(
                owingBill(11L, null, 2L, "100.00", "0"),
                owingBill(12L, null, 3L, "500.00", "0")));

        assertThat(options).extracting(FinanceViewEnricher.TenantOption::tenantRefId)
                .containsExactly(3L, 2L, 1L, 4L);
    }

    @Test
    @DisplayName("租客名口径与账单页一致:登记明细优先于档案名")
    void cashierPrefersRegisterName() {
        when(tenantMapper.selectList(any())).thenReturn(List.of(tenant(2L, "档案旧名")));
        when(registerMapper.selectBatchIds(any())).thenReturn(
                List.of(register(7L, "登记表里的租户", "XY-2026-001")));
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of(tenant(2L, "档案旧名")));

        List<FinanceViewEnricher.TenantOption> options = enricher.cashierTenantOptions(null,
                List.of(owingBill(11L, 7L, 2L, "100.00", "0")));

        assertThat(options).hasSize(1);
        assertThat(options.get(0).tenantName()).isEqualTo("登记表里的租户");
    }

    @Test
    @DisplayName("账单挂着档案里不存在的租客id时,选项也要出现(兜底名),账单不能没入口")
    void cashierKeepsOrphanTenantRefIds() {
        when(tenantMapper.selectList(any())).thenReturn(List.of());
        when(registerMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of());

        List<FinanceViewEnricher.TenantOption> options = enricher.cashierTenantOptions(null,
                List.of(owingBill(11L, null, 99L, "80.00", "0")));

        assertThat(options).hasSize(1);
        assertThat(options.get(0).tenantRefId()).isEqualTo(99L);
        assertThat(options.get(0).tenantName()).isEqualTo("租客 #99");
        assertThat(options.get(0).owe()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("欠款为0或方向不是应收的账单不计入欠款汇总")
    void cashierIgnoresNonOutstandingBills() {
        when(tenantMapper.selectList(any())).thenReturn(List.of(tenant(1L, "甲公司")));
        when(registerMapper.selectBatchIds(any())).thenReturn(List.of());
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of(tenant(1L, "甲公司")));

        Bill settled = owingBill(11L, null, 1L, "100.00", "100.00"); // 已收满
        Bill payable = owingBill(12L, null, 1L, "50.00", "0");
        payable.setDirection(2); // 应付方向,不是收银台要收的钱

        List<FinanceViewEnricher.TenantOption> options =
                enricher.cashierTenantOptions(null, List.of(settled, payable));

        assertThat(options).hasSize(1);
        assertThat(options.get(0).owe()).isEqualByComparingTo("0");
        assertThat(options.get(0).billCount()).isZero();
    }

    @Test
    @DisplayName("档案名为空且没有欠款账单可借名时,兜底成「租客 #id」,不显示空白")
    void cashierFallsBackWhenProfileNameBlank() {
        when(tenantMapper.selectList(any())).thenReturn(List.of(tenant(5L, null)));

        List<FinanceViewEnricher.TenantOption> options = enricher.cashierTenantOptions(null, null);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).tenantName()).isEqualTo("租客 #5");
        assertThat(options.get(0).owe()).isEqualByComparingTo("0");
    }

    // ---------- 档案名批量兜底(退房报表等没有账单可借名的场景) ----------

    @Test
    @DisplayName("tenantNamesOf 批量取档案名;空入参不查库")
    void tenantNamesOfBatchesAndSkipsEmpty() {
        when(tenantMapper.selectBatchIds(any())).thenReturn(List.of(
                tenant(1L, "甲公司"), tenant(2L, "  ")));

        Map<Long, String> names = enricher.tenantNamesOf(java.util.Arrays.asList(1L, 2L, null, 1L));

        // 空白名字不当名字用,调用方自己兜底
        assertThat(names).containsOnlyKeys(1L);
        assertThat(names.get(1L)).isEqualTo("甲公司");
        assertThat(enricher.tenantNamesOf(List.of())).isEmpty();
        assertThat(enricher.tenantNamesOf(null)).isEmpty();
    }
}
