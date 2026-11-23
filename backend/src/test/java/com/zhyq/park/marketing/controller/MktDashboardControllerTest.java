package com.zhyq.park.marketing.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MktDashboardControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void summaryPreservesTopLevelMetricsAndComputesAllThreeLayerDrilldownFields() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COUNT(DISTINCT p.id)"), eq(Long.class), any(), any())).thenReturn(12L);
        when(jdbc.queryForObject(contains("FROM crm_customer WHERE"), eq(Long.class), any())).thenReturn(35L);
        when(jdbc.queryForObject(contains("COUNT(*) FROM crm_referral_order"), eq(Long.class), any())).thenReturn(200L);
        when(jdbc.queryForObject(contains("SUM(service_fee)"), eq(BigDecimal.class), any())).thenReturn(new BigDecimal("1000.00"));
        when(jdbc.queryForObject(contains("status IN (2,3,4)"), eq(BigDecimal.class), any())).thenReturn(new BigDecimal("120.00"));
        when(jdbc.queryForObject(contains("FROM crm_withdrawal"), eq(Long.class))).thenReturn(3L);

        when(jdbc.queryForObject(contains("FROM crm_warehouse_settlement"), eq(BigDecimal.class), any())).thenReturn(new BigDecimal("600.00"));
        when(jdbc.queryForObject(contains("status <> 5"), eq(BigDecimal.class), any())).thenReturn(new BigDecimal("150.00"));
        when(jdbc.queryForObject(contains("FROM crm_yunshan_volume_map"), eq(Long.class))).thenReturn(6L);
        when(jdbc.queryForObject(contains("FROM crm_yunshan_daily_volume"), eq(Long.class), any())).thenReturn(4580L);
        when(jdbc.queryForObject(contains("FROM crm_warehouse"), eq(Long.class))).thenReturn(4L);
        when(jdbc.queryForObject(contains("FROM crm_erp_reconcile_snapshot"), eq(Long.class))).thenReturn(2L);
        when(jdbc.queryForObject(contains("FROM crm_erp_unmapped"), eq(Long.class))).thenReturn(5L);
        when(jdbc.queryForObject(contains("SUM(c.amount)"), eq(BigDecimal.class))).thenReturn(new BigDecimal("2180.50"));
        when(jdbc.queryForObject(contains("COUNT(*) FROM crm_promoter_commission c"), eq(Long.class))).thenReturn(2L);
        when(jdbc.queryForObject(contains("crm_customer_lock WHERE deleted = 0 AND status = 2"), eq(Long.class))).thenReturn(18L);
        when(jdbc.queryForObject(contains("crm_customer_lock WHERE deleted = 0 AND status = 3"), eq(Long.class))).thenReturn(9L);
        when(jdbc.queryForObject(contains("crm_customer_lock WHERE deleted = 0 AND status = 4"), eq(Long.class))).thenReturn(7L);

        MktDashboardController controller = new MktDashboardController(jdbc);
        Map<String, Object> data = controller.summary("30d").getData();

        assertThat(data)
                .containsEntry("activePromoters", 12L)
                .containsEntry("referrals", 35L)
                .containsEntry("outboundOrders", 200L)
                .containsEntry("serviceFee", new BigDecimal("1000.00"))
                .containsEntry("commissionPaid", new BigDecimal("120.00"))
                .containsEntry("pendingWithdrawals", 3L)
                .containsKey("drilldown");

        Map<String, Object> drilldown = (Map<String, Object>) data.get("drilldown");
        assertThat((BigDecimal) drilldown.get("serviceFeeTotal")).isEqualByComparingTo("1000.00");
        assertThat((BigDecimal) drilldown.get("warehouseSettlementCost")).isEqualByComparingTo("600.00");
        assertThat((BigDecimal) drilldown.get("validCommissionTotal")).isEqualByComparingTo("150.00");
        assertThat((BigDecimal) drilldown.get("netRetainedMargin")).isEqualByComparingTo("250.00");
        assertThat((BigDecimal) drilldown.get("marginRatePct")).isEqualByComparingTo("25.00");

        assertThat(drilldown)
                .containsEntry("yunshanActiveMaps", 6L)
                .containsEntry("yunshanTotalTickets", 4580L)
                .containsEntry("erpLiveWarehouses", 4L)
                .containsEntry("erpDiffSnapshots", 2L)
                .containsEntry("erpUnmappedPending", 5L)
                .containsEntry("nonOutboundSettleableCount", 2L)
                .containsEntry("activeLocksCount", 18L)
                .containsEntry("dealLocksCount", 9L)
                .containsEntry("releasedLocksCount", 7L);
        assertThat((BigDecimal) drilldown.get("nonOutboundSettleableAmount")).isEqualByComparingTo("2180.50");
    }

    @Test
    @SuppressWarnings("unchecked")
    void zeroServiceFeeProducesZeroMarginRateWithoutDivisionByZero() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MktDashboardController controller = new MktDashboardController(jdbc);
        Map<String, Object> drilldown = (Map<String, Object>) controller.summary("7d").getData().get("drilldown");
        assertThat((BigDecimal) drilldown.get("marginRatePct")).isEqualByComparingTo("0");
    }
}
