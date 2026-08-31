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
}
