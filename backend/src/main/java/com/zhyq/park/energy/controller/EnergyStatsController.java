package com.zhyq.park.energy.controller;

import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 能耗统计聚合接口(规格书 §14)。
 * 用 JdbcTemplate 直接聚合 eng_reading + eng_meter,只读,避免跨包依赖。
 * 注意:路由用 /energy/stats-api,避免与 MeterController 的 /energy/meter/stats 冲突。
 */
@Tag(name = "能耗管理-能耗统计")
@RestController
@RequestMapping("/energy/stats-api")
@RequiredArgsConstructor
public class EnergyStatsController {

    private static final String TYPE_ELECTRIC = "电";
    private static final String TYPE_WATER = "水";

    private final JdbcTemplate jdbc;

    private BigDecimal sum(String sql, Object... args) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Operation(summary = "能耗概览(今日/当月/当年 用量+费用,按能源类型)")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("electric", typeOverview(TYPE_ELECTRIC));
        m.put("water", typeOverview(TYPE_WATER));
        return Result.ok(m);
    }

    private Map<String, Object> typeOverview(String energyType) {
        String base = "SELECT COALESCE(SUM(r.%s),0) FROM eng_reading r "
                + "JOIN eng_meter mt ON mt.id = r.meter_id AND mt.deleted = 0 "
                + "WHERE r.deleted = 0 AND mt.energy_type = ? AND %s";

        Map<String, Object> o = new LinkedHashMap<>();
        o.put("today", sum(String.format(base, "usage_amount", "r.read_time >= CURDATE()"), energyType));
        o.put("month", sum(String.format(base, "usage_amount",
                "DATE_FORMAT(r.read_time,'%Y-%m') = DATE_FORMAT(CURDATE(),'%Y-%m')"), energyType));
        o.put("year", sum(String.format(base, "usage_amount",
                "YEAR(r.read_time) = YEAR(CURDATE())"), energyType));
        o.put("todayFee", sum(String.format(base, "fee", "r.read_time >= CURDATE()"), energyType));
        o.put("monthFee", sum(String.format(base, "fee",
                "DATE_FORMAT(r.read_time,'%Y-%m') = DATE_FORMAT(CURDATE(),'%Y-%m')"), energyType));
        o.put("yearFee", sum(String.format(base, "fee",
                "YEAR(r.read_time) = YEAR(CURDATE())"), energyType));
        return o;
    }

    @Operation(summary = "近N月用量/费用趋势(按能源类型,缺月补0)")
    @GetMapping("/trend")
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "6") int months) {
        if (months < 1) {
            months = 1;
        }
        if (months > 24) {
            months = 24;
        }
        // 生成完整月份序列(从 months-1 个月前到本月)
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1L);
        List<String> monthKeys = new ArrayList<>();
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < months; i++) {
            String key = start.plusMonths(i).format(fmt);
            idx.put(key, i);
            monthKeys.add(key);
        }

        BigDecimal[] electric = zeros(months);
        BigDecimal[] water = zeros(months);
        BigDecimal[] electricFee = zeros(months);
        BigDecimal[] waterFee = zeros(months);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT DATE_FORMAT(r.read_time,'%Y-%m') AS ym,
                       mt.energy_type AS energy_type,
                       COALESCE(SUM(r.usage_amount),0) AS usage_amount,
                       COALESCE(SUM(r.fee),0) AS fee
                FROM eng_reading r
                JOIN eng_meter mt ON mt.id = r.meter_id AND mt.deleted = 0
                WHERE r.deleted = 0
                  AND mt.energy_type IN (?, ?)
                  AND r.read_time >= DATE_SUB(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL ? MONTH)
                GROUP BY ym, energy_type
                """, TYPE_ELECTRIC, TYPE_WATER, months - 1);

        for (Map<String, Object> r : rows) {
            String ym = (String) r.get("ym");
            Integer i = idx.get(ym);
            if (i == null) {
                continue;
            }
            String type = (String) r.get("energy_type");
            BigDecimal usage = toBig(r.get("usage_amount"));
            BigDecimal fee = toBig(r.get("fee"));
            if (TYPE_ELECTRIC.equals(type)) {
                electric[i] = usage;
                electricFee[i] = fee;
            } else if (TYPE_WATER.equals(type)) {
                water[i] = usage;
                waterFee[i] = fee;
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("months", monthKeys);
        m.put("electric", Arrays.asList(electric));
        m.put("water", Arrays.asList(water));
        m.put("electricFee", Arrays.asList(electricFee));
        m.put("waterFee", Arrays.asList(waterFee));
        return Result.ok(m);
    }

    @Operation(summary = "当月各表计用量排行 Top10")
    @GetMapping("/meter-rank")
    public Result<List<Map<String, Object>>> meterRank() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT mt.name AS name,
                       mt.code AS code,
                       mt.energy_type AS energyType,
                       COALESCE(SUM(r.usage_amount),0) AS `usage`
                FROM eng_meter mt
                JOIN eng_reading r ON r.meter_id = mt.id AND r.deleted = 0
                     AND DATE_FORMAT(r.read_time,'%Y-%m') = DATE_FORMAT(CURDATE(),'%Y-%m')
                WHERE mt.deleted = 0
                GROUP BY mt.id, mt.name, mt.code, mt.energy_type
                ORDER BY `usage` DESC
                LIMIT 10
                """);
        return Result.ok(rows);
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] arr = new BigDecimal[n];
        Arrays.fill(arr, BigDecimal.ZERO);
        return arr;
    }

    private static BigDecimal toBig(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }
}
