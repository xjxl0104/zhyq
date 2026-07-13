package com.zhyq.park.crm.controller;

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
 * 招商分析驾驶舱聚合。用 JdbcTemplate 直接聚合,只读,常量 SQL。
 */
@Tag(name = "招商-招商分析")
@RestController
@RequestMapping("/crm/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final JdbcTemplate jdbc;

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private BigDecimal sum(String sql) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }

    private Map<String, Object> item(String name, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    @Operation(summary = "招商漏斗")
    @GetMapping("/funnel")
    public Result<List<Map<String, Object>>> funnel() {
        long total = count("SELECT COUNT(*) FROM crm_lead WHERE deleted=0");
        long following = count("SELECT COUNT(*) FROM crm_lead WHERE status IN (2,3) AND deleted=0");
        long intent = count("SELECT COUNT(*) FROM crm_lead WHERE status=4 AND deleted=0")
                + count("SELECT COUNT(*) FROM crm_customer WHERE status=1 AND deleted=0");
        long converted = count("SELECT COUNT(*) FROM crm_lead WHERE status=5 AND deleted=0");
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(item("总线索", total));
        out.add(item("跟进中", following));
        out.add(item("意向", intent));
        out.add(item("已转化", converted));
        return Result.ok(out);
    }

    @Operation(summary = "近6月线索与转化趋势")
    @GetMapping("/trend")
    public Result<Map<String, Object>> trend() {
        List<Map<String, Object>> leadRows = jdbc.queryForList(
                "SELECT DATE_FORMAT(create_time,'%Y-%m') AS ym, COUNT(*) AS cnt " +
                "FROM crm_lead WHERE deleted=0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH) " +
                "GROUP BY ym ORDER BY ym");
        List<Map<String, Object>> convRows = jdbc.queryForList(
                "SELECT DATE_FORMAT(create_time,'%Y-%m') AS ym, COUNT(*) AS cnt " +
                "FROM crm_lead WHERE deleted=0 AND status=5 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH) " +
                "GROUP BY ym ORDER BY ym");

        Map<String, Object> leadMap = new LinkedHashMap<>();
        for (Map<String, Object> r : leadRows) {
            leadMap.put((String) r.get("ym"), r.get("cnt"));
        }
        Map<String, Object> convMap = new LinkedHashMap<>();
        for (Map<String, Object> r : convRows) {
            convMap.put((String) r.get("ym"), r.get("cnt"));
        }

        List<String> months = new ArrayList<>(leadMap.keySet());
        for (String ym : convMap.keySet()) {
            if (!months.contains(ym)) {
                months.add(ym);
            }
        }
        Collections.sort(months);

        List<Object> leads = new ArrayList<>();
        List<Object> converted = new ArrayList<>();
        for (String ym : months) {
            leads.add(leadMap.getOrDefault(ym, 0));
            converted.add(convMap.getOrDefault(ym, 0));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("months", months);
        m.put("leads", leads);
        m.put("converted", converted);
        return Result.ok(m);
    }

    @Operation(summary = "线索来源分布")
    @GetMapping("/source")
    public Result<List<Map<String, Object>>> source() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE(NULLIF(source,''),'未知') AS name, COUNT(*) AS value " +
                "FROM crm_lead WHERE deleted=0 GROUP BY name");
        return Result.ok(rows);
    }

    @Operation(summary = "招商概览")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerTotal", count("SELECT COUNT(*) FROM crm_customer WHERE deleted=0"));
        m.put("levelA", count("SELECT COUNT(*) FROM crm_customer WHERE intent_level='A' AND deleted=0"));
        m.put("signed", count("SELECT COUNT(*) FROM crm_customer WHERE status=2 AND deleted=0"));
        m.put("commissionPending", sum("SELECT COALESCE(SUM(commission),0) FROM crm_commission WHERE status=1 AND deleted=0"));
        m.put("monthLead", count("SELECT COUNT(*) FROM crm_lead WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=DATE_FORMAT(CURDATE(),'%Y-%m')"));
        return Result.ok(m);
    }
}
