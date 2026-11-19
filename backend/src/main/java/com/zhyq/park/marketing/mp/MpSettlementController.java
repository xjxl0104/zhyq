package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.service.MktMonthlySettlementService;
import com.zhyq.park.marketing.service.MktWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 伙伴「结算提现」:本人佣金的资金流转(冻结 → 已解冻待结算 → 已结算可提现 → 提现审批中 → 已提现),
 * 按合同汇总的出库佣金(点进合同看每月),以及自动结算 / 自动提现的时间节点。只返回本人数据。
 */
@Tag(name = "小程序-结算提现")
@RestController
@RequestMapping("/mp/v1/settlement")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MP')")
public class MpSettlementController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final MktMonthlySettlementService monthly;
    private final MktWithdrawalService withdrawals;
    private final com.zhyq.park.common.setting.BizSettings settings;

    @Operation(summary = "结算提现总览")
    @GetMapping
    public Result<Map<String, Object>> overview() {
        Long pid = MpAuthService.currentPromoterId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totals", totals(pid));
        out.put("contracts", contracts(monthly.monthlySummary(List.of(pid))));
        out.put("schedule", schedule(LocalDate.now(ZONE)));
        out.put("withdrawals", recentWithdrawals(pid));
        return Result.ok(out);
    }

    private Map<String, Object> totals(Long pid) {
        Map<String, Object> c = jdbc.queryForMap(
                "SELECT COALESCE(SUM(CASE WHEN status <> " + MktCommissionService.C_VOID + " THEN amount END), 0) AS total, "
                        + "COALESCE(SUM(CASE WHEN status = " + MktCommissionService.C_FROZEN + " THEN amount END), 0) AS frozen, "
                        + "COALESCE(SUM(CASE WHEN status = " + MktCommissionService.C_SETTLEABLE + " AND sign = 1 AND withdrawal_id IS NULL THEN amount END), 0) AS settleable "
                        + "FROM crm_promoter_commission WHERE deleted = 0 AND promoter_id = ?", pid);
        Map<String, Object> w = jdbc.queryForMap(
                "SELECT COALESCE(SUM(CASE WHEN status IN (" + MktWithdrawalService.WS_PENDING + "," + MktWithdrawalService.WS_APPROVED + ") THEN amount END), 0) AS withdrawing, "
                        + "COALESCE(SUM(CASE WHEN status = " + MktWithdrawalService.WS_PAID + " THEN amount END), 0) AS withdrawn "
                        + "FROM crm_withdrawal WHERE deleted = 0 AND promoter_id = ?", pid);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", c.get("total"));
        m.put("frozen", c.get("frozen"));
        m.put("settleable", c.get("settleable"));
        m.put("withdrawable", withdrawals.balance(pid).max(BigDecimal.ZERO));
        m.put("withdrawing", w.get("withdrawing"));
        m.put("withdrawn", w.get("withdrawn"));
        return m;
    }

    /** 月×合同行按合同归并:合同一行汇总,months 为该合同各月份(新→旧)。 */
    static List<Map<String, Object>> contracts(List<Map<String, Object>> rows) {
        Map<Object, Map<String, Object>> byContract = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> c = byContract.computeIfAbsent(r.get("contract_id"), id -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("contractId", id); m.put("contractNo", r.get("contract_no")); m.put("customerName", r.get("customer_name"));
                for (String k : List.of("total", "frozen", "settleable", "settled")) m.put(k, BigDecimal.ZERO);
                m.put("tickets", 0L); m.put("months", new ArrayList<Map<String, Object>>());
                return m;
            });
            Map<String, Object> month = new LinkedHashMap<>();
            month.put("month", r.get("ym"));
            for (String k : List.of("total", "frozen", "settleable", "settled")) {
                BigDecimal v = r.get(k) == null ? BigDecimal.ZERO : new BigDecimal(r.get(k).toString());
                month.put(k, v);
                c.put(k, ((BigDecimal) c.get(k)).add(v));
            }
            long tickets = r.get("tickets") instanceof Number n ? n.longValue() : 0L;
            month.put("tickets", tickets);
            c.put("tickets", (Long) c.get("tickets") + tickets);
            @SuppressWarnings("unchecked") List<Map<String, Object>> months = (List<Map<String, Object>>) c.get("months");
            months.add(month);
        }
        for (Map<String, Object> c : byContract.values()) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> months = (List<Map<String, Object>>) c.get("months");
            c.put("latestMonth", months.isEmpty() ? null : months.get(0).get("month"));
            c.put("monthCount", months.size());
        }
        return new ArrayList<>(byContract.values());
    }

    private Map<String, Object> schedule(LocalDate today) {
        int day = withdrawals.autoWithdrawDay();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("freezeDays", settings.getInt("marketing", "freeze_days", 7));
        m.put("settleTime", "每天 02:00");
        m.put("autoWithdrawDay", day);
        if (day > 0) {
            LocalDate next = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
            if (!next.isAfter(today)) {
                LocalDate nm = today.plusMonths(1);
                next = nm.withDayOfMonth(Math.min(day, nm.lengthOfMonth()));
            }
            m.put("nextAutoWithdraw", next.toString());
        }
        m.put("rules", withdrawals.withdrawalRules());
        return m;
    }

    private List<Map<String, Object>> recentWithdrawals(Long pid) {
        return jdbc.queryForList(
                "SELECT w.id, w.withdrawal_no AS withdrawalNo, w.amount, w.net_amount AS netAmount, w.status, w.reject_reason AS rejectReason, "
                        + "w.pay_no AS payNo, w.pay_at AS payAt, w.create_time AS createTime, w.audit_at AS auditAt, "
                        + "(SELECT n.name FROM wf_instance i JOIN wf_task t ON t.instance_id = i.id AND t.seq = i.current_seq AND t.deleted = 0 "
                        + " JOIN wf_node n ON n.id = t.node_id WHERE i.deleted = 0 AND i.biz_type = 'withdrawal' AND i.biz_id = w.id "
                        + " ORDER BY i.id DESC, t.id DESC LIMIT 1) AS nodeName "
                        + "FROM crm_withdrawal w WHERE w.deleted = 0 AND w.promoter_id = ? ORDER BY w.id DESC LIMIT 5", pid);
    }
}
