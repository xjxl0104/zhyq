package com.zhyq.park.marketing.controller;

import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
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
        Map<String, Object> m = new HashMap<>();
        m.put("activePromoters", jdbc.queryForObject(
                "SELECT COUNT(DISTINCT p.id) FROM crm_promoter p WHERE p.deleted = 0 AND p.status = 1 AND (" +
                " EXISTS (SELECT 1 FROM crm_customer c WHERE c.referrer_id = p.id AND c.deleted = 0 AND c.create_time >= ?)" +
                " OR EXISTS (SELECT 1 FROM crm_promoter_commission pc WHERE pc.promoter_id = p.id AND pc.deleted = 0 AND pc.create_time >= ?))",
                Long.class, since.minusDays(30), since.minusDays(30)));
        m.put("referrals", jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE deleted = 0 AND referrer_id IS NOT NULL AND create_time >= ?", Long.class, since));
        m.put("outboundOrders", jdbc.queryForObject("SELECT COUNT(*) FROM crm_referral_order WHERE deleted = 0 AND source_type = 2 AND status = 2 AND event_time >= ?", Long.class, since));
        m.put("serviceFee", jdbc.queryForObject("SELECT COALESCE(SUM(service_fee),0) FROM crm_referral_order WHERE deleted = 0 AND source_type = 2 AND status = 2 AND event_time >= ?", java.math.BigDecimal.class, since));
        m.put("commissionPaid", jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM crm_promoter_commission WHERE deleted = 0 AND status IN (2,3,4) AND create_time >= ?", java.math.BigDecimal.class, since));
        m.put("pendingWithdrawals", jdbc.queryForObject("SELECT COUNT(*) FROM crm_withdrawal WHERE deleted = 0 AND status = 1", Long.class));
        return Result.ok(m);
    }

    @Operation(summary = "漏斗:推荐 → 到访 → 签约 → 履约") @PreAuthorize("hasAuthority('crm:marketing:dashboard:query')") @GetMapping("/funnel")
    public Result<List<Map<String, Object>>> funnel(@RequestParam(defaultValue = "30d") String range) {
        LocalDate since = since(range);
        long referred = count("SELECT COUNT(*) FROM crm_customer WHERE deleted = 0 AND referrer_id IS NOT NULL AND create_time >= ?", since);
        long visited = count("SELECT COUNT(DISTINCT c.id) FROM crm_customer c JOIN crm_follow f ON f.lead_id = c.source_lead_id WHERE c.deleted = 0 AND c.referrer_id IS NOT NULL AND c.create_time >= ?", since);
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
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
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
