package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 看板(PARK-MKT-001 §6.3 口径),直接 SQL 聚合;伙伴/订单量级上来后再换每日聚合表。 */
@Tag(name = "全民营销-看板")
@RestController
@RequestMapping("/crm/marketing/dashboard")
@RequiredArgsConstructor
public class MktDashboardController {

    private final JdbcTemplate jdbc;

    @Operation(summary = "汇总卡片") @PreAuthorize("hasAuthority('crm:marketing:dashboard:query')") @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(defaultValue = "30d") String range) {
        LocalDate since = since(range);
        LocalDate activityStart = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(29);
        Map<String, Object> m = new HashMap<>();
        m.put("activePromoters", jdbc.queryForObject(
                "SELECT COUNT(DISTINCT p.id) FROM crm_promoter p WHERE p.deleted = 0 AND p.status = 1 AND (" +
                " EXISTS (SELECT 1 FROM crm_customer c WHERE c.referrer_id = p.id AND c.deleted = 0 AND c.create_time >= ?)" +
                " OR EXISTS (SELECT 1 FROM crm_promoter_commission pc WHERE pc.promoter_id = p.id AND pc.deleted = 0 AND pc.create_time >= ?))",
                Long.class, activityStart, activityStart));
        m.put("referrals", jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE deleted = 0 AND referrer_id IS NOT NULL AND create_time >= ?", Long.class, since));
        m.put("outboundOrders", jdbc.queryForObject("SELECT COUNT(*) FROM crm_referral_order WHERE deleted = 0 AND source_type = 2 AND status = 2 AND event_time >= ?", Long.class, since));
        BigDecimal serviceFee = decimal("SELECT COALESCE(SUM(service_fee),0) FROM crm_referral_order WHERE deleted = 0 AND source_type = 2 AND status = 2 AND event_time >= ?", since);
        m.put("serviceFee", serviceFee);
        m.put("commissionPaid", decimal("SELECT COALESCE(SUM(amount),0) FROM crm_promoter_commission WHERE deleted = 0 AND status IN (2,3,4) AND create_time >= ?", since));
        m.put("pendingWithdrawals", count("SELECT COUNT(*) FROM crm_withdrawal WHERE deleted = 0 AND status = 1"));

        // 三层下钻(L1→L2→L3)同源聚合子对象,单次请求返回完整对账与转化明细
        Map<String, Object> drilldown = new LinkedHashMap<>();
        BigDecimal warehouseSettlementCost = decimal(
                "SELECT COALESCE(SUM(amount), 0) FROM crm_warehouse_settlement WHERE deleted = 0 AND create_time >= ?", since);
        BigDecimal validCommissionTotal = decimal(
                "SELECT COALESCE(SUM(amount), 0) FROM crm_promoter_commission WHERE deleted = 0 AND status <> 5 AND create_time >= ?", since);
        BigDecimal netRetainedMargin = serviceFee.subtract(warehouseSettlementCost).subtract(validCommissionTotal);
        BigDecimal marginRatePct = serviceFee.signum() > 0
                ? netRetainedMargin.multiply(new BigDecimal("100")).divide(serviceFee, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        drilldown.put("serviceFeeTotal", serviceFee);
        drilldown.put("warehouseSettlementCost", warehouseSettlementCost);
        drilldown.put("validCommissionTotal", validCommissionTotal);
        drilldown.put("netRetainedMargin", netRetainedMargin);
        drilldown.put("marginRatePct", marginRatePct);

        drilldown.put("yunshanActiveMaps", count(
                "SELECT COUNT(*) FROM crm_yunshan_volume_map WHERE deleted = 0 AND status = 1"));
        drilldown.put("yunshanTotalTickets", count(
                "SELECT COALESCE(SUM(consign_num), 0) FROM crm_yunshan_daily_volume WHERE deleted = 0 AND stat_date >= ?", since));
        drilldown.put("erpLiveWarehouses", count(
                "SELECT COUNT(*) FROM crm_warehouse WHERE deleted = 0 AND join_status = 5 AND erp_status = 2 AND (order_mode = 'erp' OR order_mode IS NULL)"));
        drilldown.put("erpDiffSnapshots", count(
                "SELECT COUNT(*) FROM crm_erp_reconcile_snapshot WHERE deleted = 0 AND (state IS NULL OR state <> 'CONFIRMED') "
                        + "AND (ABS(COALESCE(diff_ratio, 0)) >= 0.02 "
                        + "OR (GREATEST(COALESCE(external_order_count, 0), COALESCE(system_order_count, 0)) > 0 "
                        + "AND ABS(COALESCE(diff_order_count, 0)) * 100 >= GREATEST(COALESCE(external_order_count, 0), COALESCE(system_order_count, 0)) * 2))"));
        drilldown.put("erpUnmappedPending", count(
                "SELECT COUNT(*) FROM crm_erp_unmapped WHERE deleted = 0 AND status = 'OPEN'"));

        drilldown.put("nonOutboundSettleableAmount", decimal(
                "SELECT COALESCE(SUM(c.amount), 0) FROM crm_promoter_commission c "
                        + "JOIN crm_referral_order o ON o.id = c.referral_order_id AND o.deleted = 0 "
                        + "WHERE c.deleted = 0 AND o.source_type IN (1, 3, 4) AND c.status = 2 AND c.sign = 1 AND c.withdrawal_id IS NULL"));
        drilldown.put("nonOutboundSettleableCount", count(
                "SELECT COUNT(*) FROM crm_promoter_commission c "
                        + "JOIN crm_referral_order o ON o.id = c.referral_order_id AND o.deleted = 0 "
                        + "WHERE c.deleted = 0 AND o.source_type IN (1, 3, 4) AND c.status = 2 AND c.sign = 1 AND c.withdrawal_id IS NULL"));

        drilldown.put("activeLocksCount", count("SELECT COUNT(*) FROM crm_customer_lock WHERE deleted = 0 AND status = 2"));
        drilldown.put("dealLocksCount", count("SELECT COUNT(*) FROM crm_customer_lock WHERE deleted = 0 AND status = 3"));
        drilldown.put("releasedLocksCount", count("SELECT COUNT(*) FROM crm_customer_lock WHERE deleted = 0 AND status = 4"));

        m.put("drilldown", drilldown);
        return Result.ok(m);
    }

    @Operation(summary = "漏斗:推荐 → 到访 → 签约 → 履约") @PreAuthorize("hasAuthority('crm:marketing:dashboard:query')") @GetMapping("/funnel")
    public Result<List<Map<String, Object>>> funnel(@RequestParam(defaultValue = "30d") String range) {
        LocalDate since = since(range);
        long referred = count("SELECT COUNT(*) FROM crm_customer WHERE deleted = 0 AND referrer_id IS NOT NULL AND create_time >= ?", since);
        long visited = count("""
                SELECT COUNT(*) FROM crm_customer c JOIN crm_lead l ON l.id = c.source_lead_id AND l.deleted = 0
                WHERE c.deleted = 0 AND c.referrer_id IS NOT NULL AND c.create_time >= ?
                AND (l.source = '上门到访' OR EXISTS (SELECT 1 FROM crm_follow f
                     WHERE f.lead_id = l.id AND f.deleted = 0 AND f.type IN ('客户到访', '上门到访')))
                """, since);
        long signed = count("SELECT COUNT(DISTINCT customer_id) FROM crm_service_contract WHERE deleted = 0 AND status >= 4 AND status <= 7 AND create_time >= ?", since);
        long performing = count("SELECT COUNT(DISTINCT customer_id) FROM crm_referral_order WHERE deleted = 0 AND source_type = 2 AND status = 2 AND event_time >= ?", since);
        return Result.ok(List.of(stage("推荐", referred), stage("已到访", Math.min(visited, referred)), stage("签约", signed), stage("履约", performing)));
    }

    @Operation(summary = "趋势:按日出库单量 / 服务费 / 佣金") @PreAuthorize("hasAuthority('crm:marketing:dashboard:query')") @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30d") String range) {
        LocalDate since = since(range);
        return Result.ok(jdbc.queryForList(
                "SELECT d.day, COALESCE(o.orders,0) AS orders, COALESCE(o.service_fee,0) AS serviceFee, COALESCE(c.commission,0) AS commission FROM (" +
                "  SELECT DATE(event_time) AS day FROM crm_referral_order WHERE deleted = 0 AND event_time >= ? " +
                "  UNION SELECT DATE(create_time) FROM crm_promoter_commission WHERE deleted = 0 AND create_time >= ?) d " +
                "LEFT JOIN (SELECT DATE(event_time) day, COUNT(*) orders, SUM(service_fee) service_fee FROM crm_referral_order WHERE deleted = 0 AND source_type = 2 AND status = 2 AND event_time >= ? GROUP BY DATE(event_time)) o ON o.day = d.day " +
                "LEFT JOIN (SELECT DATE(create_time) day, SUM(amount) commission FROM crm_promoter_commission WHERE deleted = 0 AND status IN (2,3,4) AND create_time >= ? GROUP BY DATE(create_time)) c ON c.day = d.day " +
                "ORDER BY d.day", since, since, since, since));
    }

    private long count(String sql, Object... args) {
        Long v = (args == null || args.length == 0)
                ? jdbc.queryForObject(sql, Long.class)
                : jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private BigDecimal decimal(String sql, Object... args) {
        BigDecimal v = (args == null || args.length == 0)
                ? jdbc.queryForObject(sql, BigDecimal.class)
                : jdbc.queryForObject(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }

    private static Map<String, Object> stage(String name, long count) {
        Map<String, Object> m = new HashMap<>();
        m.put("stage", name); m.put("count", count);
        return m;
    }

    private static LocalDate since(String range) {
        int days = switch (range) { case "7d" -> 7; case "90d" -> 90; default -> 30; };
        return LocalDate.now().minusDays(days);
    }
}
