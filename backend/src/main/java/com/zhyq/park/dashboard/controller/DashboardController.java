package com.zhyq.park.dashboard.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

/**
 * 驾驶舱 / 首页工作台 / 大屏 统一聚合接口。
 * 用 JdbcTemplate 直接聚合,跨模块只读,避免大量跨包 mapper 依赖。
 */
@Tag(name = "数据中心-驾驶舱聚合")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final JdbcTemplate jdbc;

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private BigDecimal sum(String sql) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Operation(summary = "首页工作台概览")
    @GetMapping("/workbench")
    public Result<Map<String, Object>> workbench() {
        Map<String, Object> m = new LinkedHashMap<>();
        // 待办类指标
        m.put("contractPending", count("SELECT COUNT(*) FROM biz_contract WHERE status=2 AND deleted=0"));
        m.put("contractExpiring", count("SELECT COUNT(*) FROM biz_contract WHERE status=5 AND deleted=0 AND end_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY)"));
        m.put("leadFollow", count("SELECT COUNT(*) FROM crm_lead WHERE status=3 AND deleted=0"));
        m.put("approvalPending", count("SELECT COUNT(*) FROM biz_approval WHERE status=2 AND deleted=0"));
        m.put("todoCount", count("SELECT COUNT(*) FROM sys_todo WHERE status=1 AND deleted=0"));
        m.put("billUnpaid", count("SELECT COUNT(*) FROM fin_bill WHERE status IN (3,4,6) AND deleted=0"));
        m.put("workOrderPending", count("SELECT COUNT(*) FROM pm_work_order WHERE status IN (1,2,3) AND deleted=0"));
        return Result.ok(m);
    }

    @Operation(summary = "数据中心/大屏 综合看板")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> m = new LinkedHashMap<>();

        // 财务数字概览
        Map<String, Object> fin = new LinkedHashMap<>();
        fin.put("dueReceivable", sum("SELECT COALESCE(SUM(amount-paid_amount),0) FROM fin_bill WHERE direction=1 AND status IN (3,4,6) AND deleted=0"));
        fin.put("future30", sum("SELECT COALESCE(SUM(amount),0) FROM fin_bill WHERE direction=1 AND status=3 AND due_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY) AND deleted=0"));
        fin.put("overdue", sum("SELECT COALESCE(SUM(amount-paid_amount),0) FROM fin_bill WHERE status=6 AND deleted=0"));
        fin.put("received", sum("SELECT COALESCE(SUM(paid_amount),0) FROM fin_bill WHERE deleted=0"));
        m.put("finance", fin);

        // 经营收入来源：租费账单沿用财务权威口径，售货机只统计已受控导入的本地销售副本。
        Map<String, Object> incomeSources = new LinkedHashMap<>();
        incomeSources.put("rentPropertyBilled", sum("""
                SELECT COALESCE(SUM(amount),0) FROM fin_bill
                WHERE direction=1 AND fee_type IN ('租金','物业费') AND deleted=0
                """));
        incomeSources.put("rentPropertyReceived", sum("""
                SELECT COALESCE(SUM(paid_amount),0) FROM fin_bill
                WHERE direction=1 AND fee_type IN ('租金','物业费') AND deleted=0
                """));
        incomeSources.put("vendingSales", sum("""
                SELECT COALESCE(SUM(paid_amount),0) FROM ops_vending_sale
                WHERE order_status NOT IN ('已退款','已取消') AND deleted=0
                """));
        m.put("incomeSources", incomeSources);

        // 合同执行
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("total", count("SELECT COUNT(*) FROM biz_contract WHERE deleted=0"));
        contract.put("executing", count("SELECT COUNT(*) FROM biz_contract WHERE status=5 AND deleted=0"));
        contract.put("terminated", count("SELECT COUNT(*) FROM biz_contract WHERE status=9 AND deleted=0"));
        contract.put("expired", count("SELECT COUNT(*) FROM biz_contract WHERE status=8 AND deleted=0"));
        m.put("contract", contract);

        // 设备
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("total", count("SELECT COUNT(*) FROM iot_device WHERE deleted=0"));
        device.put("online", count("SELECT COUNT(*) FROM iot_device WHERE status=1 AND deleted=0"));
        device.put("offline", count("SELECT COUNT(*) FROM iot_device WHERE status=2 AND deleted=0"));
        device.put("alarm", count("SELECT COUNT(*) FROM iot_alarm WHERE status IN (1,2,3) AND deleted=0"));
        m.put("device", device);

        // 房源
        long totalRoom = count("SELECT COUNT(*) FROM biz_room WHERE deleted=0");
        long rented = count("SELECT COUNT(*) FROM biz_room WHERE status=5 AND deleted=0");
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("total", totalRoom);
        room.put("rented", rented);
        room.put("vacant", count("SELECT COUNT(*) FROM biz_room WHERE status=1 AND deleted=0"));
        room.put("rentRate", totalRoom == 0 ? 0 : Math.round(rented * 1000.0 / totalRoom) / 10.0);
        room.put("avgPrice", sum("SELECT COALESCE(AVG(base_price),0) FROM biz_room WHERE status=5 AND deleted=0"));
        m.put("room", room);

        // 租客 & 工单
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("tenantTotal", count("SELECT COUNT(*) FROM biz_tenant WHERE deleted=0"));
        other.put("leadTotal", count("SELECT COUNT(*) FROM crm_lead WHERE deleted=0"));
        other.put("workOrderOpen", count("SELECT COUNT(*) FROM pm_work_order WHERE status IN (1,2,3) AND deleted=0"));
        m.put("other", other);

        return Result.ok(m);
    }

    @Operation(summary = "房源状态分布(大屏饼图)")
    @GetMapping("/room-status")
    public Result<List<Map<String, Object>>> roomStatus() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM biz_room WHERE deleted=0 GROUP BY status");
        Map<Integer, String> names = Map.of(
                0, "未配置", 1, "可租", 2, "锁定", 3, "意向占用", 4, "签约中",
                5, "在租", 6, "退租处理中", 7, "维修", 8, "停用");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            int st = ((Number) r.get("status")).intValue();
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", names.getOrDefault(st, "其它"));
            o.put("value", r.get("cnt"));
            out.add(o);
        }
        return Result.ok(out);
    }

    @Operation(summary = "近6月应收/实收趋势(大屏折线)")
    @GetMapping("/revenue-trend")
    public Result<Map<String, Object>> revenueTrend() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT DATE_FORMAT(due_date,'%Y-%m') AS ym,
                       COALESCE(SUM(amount),0) AS receivable,
                       COALESCE(SUM(paid_amount),0) AS received
                FROM fin_bill
                WHERE direction=1 AND deleted=0
                  AND due_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
                GROUP BY ym ORDER BY ym
                """);
        List<String> months = new ArrayList<>();
        List<Object> receivable = new ArrayList<>();
        List<Object> received = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            months.add((String) r.get("ym"));
            receivable.add(r.get("receivable"));
            received.add(r.get("received"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("months", months);
        m.put("receivable", receivable);
        m.put("received", received);
        return Result.ok(m);
    }

    @Operation(summary = "工单分类统计(大屏柱状)")
    @GetMapping("/workorder-category")
    public Result<List<Map<String, Object>>> workOrderCategory() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE(category,'其它') AS name, COUNT(*) AS value FROM pm_work_order WHERE deleted=0 GROUP BY category");
        return Result.ok(rows);
    }
}
