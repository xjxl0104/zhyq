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
 *
 * <p><b>用量口径(2026-09-12 起)</b>:园区一种能源只有一张对外发票,对应发票总表(MAIN)读数;
 * 租户分表(TENANT)、物业公司表(PROPERTY)都是总表之下的分表。把总表和分表一起相加会把
 * 同一方水算两遍(真测:8 月水 总表 1012 + 分表 562.5 + 一块参考表 710 被加成 2284.5)。
 * 所以:总用量 = 总表读数;总表和分表同期都有读数时再拆 租户 / 物业 / 公摊(= 总表 − 租户 − 物业);
 * 该期没有总表读数(如园区电表没装总表)则退回「分表合计」,用 hasMain=false 标明。
 * 参考表(REFERENCE,如总表之下的分总表)只记录读数,不进用量统计与分摊。
 * 费用永远按分表合计:总表本身不出账。</p>
 *
 * <p><b>跨月窗口逐月合成</b>:总表和分表不一定同一天抄,「当年」这种跨月窗口先按月各自定口径再相加,
 * 不会因为某个月总表还没抄就把那个月的分表用量从别的月的总表里减掉(公摊算成负数)。
 * 缺总表读数的月份数以 monthsWithoutMain 带回,前端标注。同一个月内总表与分表读数日期不同的情况
 * 仍按「月」看,这是抄表口径本身的粒度。</p>
 *
 * <p><b>多块总表守卫</b>:一种能源只该有一块在用的 MAIN(MeterController 新增/修改时拦),
 * 这里仍把 mainMeters 带回前端提示,防止历史数据或直接改库绕过。</p>
 */
@Tag(name = "能耗管理-能耗统计")
@RestController
@RequestMapping("/energy/stats-api")
@RequiredArgsConstructor
public class EnergyStatsController {

    private static final String TYPE_ELECTRIC = "电";
    private static final String TYPE_WATER = "水";
    static final String ROLE_MAIN = "MAIN";
    static final String ROLE_TENANT = "TENANT";
    static final String ROLE_PROPERTY = "PROPERTY";

    private final JdbcTemplate jdbc;

    /**
     * 一个时间窗内、一种能源合成后的口径结果。
     * publicUsage 在「没有总表读数」或「有总表但分表没抄」时为 null(拆不出来就不拆,不给假数);
     * mainMeters 是窗口内有读数的 MAIN 表计块数(>1 说明角色配错了);
     * monthsWithoutMain 是跨月窗口里缺总表读数、按分表合计的月份数。
     */
    record Usage(boolean hasMain, int mainMeters, BigDecimal total, BigDecimal tenant, BigDecimal property,
                 BigDecimal publicUsage, BigDecimal fee, int monthsWithoutMain) {
        static final Usage EMPTY = new Usage(false, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, 0);
    }

    /**
     * 把「一个月内按角色分组」的聚合行合成口径结果。每行要有 meter_role / meters / usage_amount / fee 四列。
     * 抽成纯函数是为了让口径能被单元测试钉死(JdbcTemplate 在测试里是 mock)。
     */
    static Usage combine(List<Map<String, Object>> rowsByRole) {
        BigDecimal main = BigDecimal.ZERO;
        BigDecimal tenant = BigDecimal.ZERO;
        BigDecimal property = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        boolean hasMain = false;
        boolean hasSub = false;
        int mainMeters = 0;
        for (Map<String, Object> r : rowsByRole) {
            String role = String.valueOf(r.get("meter_role"));
            BigDecimal usage = toBig(r.get("usage_amount"));
            switch (role) {
                case ROLE_MAIN -> {
                    main = main.add(usage);
                    hasMain = true;
                    mainMeters += toBig(r.get("meters")).intValue();
                }
                case ROLE_TENANT -> {
                    tenant = tenant.add(usage);
                    fee = fee.add(toBig(r.get("fee")));
                    hasSub = true;
                }
                case ROLE_PROPERTY -> {
                    property = property.add(usage);
                    fee = fee.add(toBig(r.get("fee")));
                    hasSub = true;
                }
                default -> {
                    // REFERENCE 等其它角色:只记录读数,不进统计
                }
            }
        }
        BigDecimal submeters = tenant.add(property);
        BigDecimal total = hasMain ? main : submeters;
        BigDecimal publicUsage = hasMain && hasSub ? main.subtract(submeters) : null;
        return new Usage(hasMain, mainMeters, total, tenant, property, publicUsage, fee, 0);
    }

    /** 跨月窗口:各月先各自定口径,再相加。公摊只加「拆得出来」的月份,一个月都拆不出就是 null */
    static Usage sumMonths(Collection<Usage> months) {
        if (months.isEmpty()) {
            return Usage.EMPTY;
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tenant = BigDecimal.ZERO;
        BigDecimal property = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal publicUsage = null;
        boolean hasMain = false;
        int mainMeters = 0;
        int monthsWithoutMain = 0;
        for (Usage u : months) {
            total = total.add(u.total());
            tenant = tenant.add(u.tenant());
            property = property.add(u.property());
            fee = fee.add(u.fee());
            hasMain |= u.hasMain();
            mainMeters = Math.max(mainMeters, u.mainMeters());
            if (!u.hasMain()) {
                monthsWithoutMain++;
            }
            if (u.publicUsage() != null) {
                publicUsage = (publicUsage == null ? BigDecimal.ZERO : publicUsage).add(u.publicUsage());
            }
        }
        return new Usage(hasMain, mainMeters, total, tenant, property, publicUsage, fee, monthsWithoutMain);
    }

    /** 按 ym 归组后逐月合成,键按月份升序 */
    static Map<String, Usage> monthly(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> grouped = new TreeMap<>();
        for (Map<String, Object> r : rows) {
            grouped.computeIfAbsent(String.valueOf(r.get("ym")), k -> new ArrayList<>()).add(r);
        }
        Map<String, Usage> out = new LinkedHashMap<>();
        grouped.forEach((ym, list) -> out.put(ym, combine(list)));
        return out;
    }

    // 注意本串会过 String.format,SQL 自己的 % 要写成 %%;%s 位置放时间过滤条件(代码常量,无注入面)
    private static final String BY_MONTH_ROLE_SQL = "SELECT DATE_FORMAT(r.read_time,'%%Y-%%m') AS ym, "
            + "mt.meter_role, COUNT(DISTINCT mt.id) AS meters, "
            + "COALESCE(SUM(r.usage_amount),0) AS usage_amount, COALESCE(SUM(r.fee),0) AS fee "
            + "FROM eng_reading r JOIN eng_meter mt ON mt.id = r.meter_id AND mt.deleted = 0 "
            + "WHERE r.deleted = 0 AND mt.energy_type = ? AND %s "
            + "GROUP BY ym, mt.meter_role";

    private Map<String, Usage> monthlyUsage(String energyType, String timeFilter, Object... extraArgs) {
        Object[] args = new Object[1 + extraArgs.length];
        args[0] = energyType;
        System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
        return monthly(jdbc.queryForList(String.format(BY_MONTH_ROLE_SQL, timeFilter), args));
    }

    private Usage usageIn(String energyType, String timeFilter) {
        return sumMonths(monthlyUsage(energyType, timeFilter).values());
    }

    @Operation(summary = "能耗概览(今日/当月/当年 用量+费用,按能源类型;总表口径,附租户/物业/公摊拆分)")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("electric", typeOverview(TYPE_ELECTRIC));
        m.put("water", typeOverview(TYPE_WATER));
        return Result.ok(m);
    }

    private Map<String, Object> typeOverview(String energyType) {
        Map<String, Object> o = new LinkedHashMap<>();
        putWindow(o, "today", usageIn(energyType, "r.read_time >= CURDATE()"));
        putWindow(o, "month", usageIn(energyType,
                "DATE_FORMAT(r.read_time,'%Y-%m') = DATE_FORMAT(CURDATE(),'%Y-%m')"));
        putWindow(o, "year", usageIn(energyType, "YEAR(r.read_time) = YEAR(CURDATE())"));
        return o;
    }

    /**
     * 老键 today / todayFee 保留(总用量、分表费用);拆分项以
     * todayTenant / todayProperty / todayPublic / todayHasMain / todayMainMeters / todayMonthsWithoutMain 追加。
     * 前端 Stats.vue 按这些键名取值,改名要一起改(有测试钉住)。
     */
    static void putWindow(Map<String, Object> o, String key, Usage u) {
        o.put(key, u.total());
        o.put(key + "Fee", u.fee());
        o.put(key + "Tenant", u.tenant());
        o.put(key + "Property", u.property());
        o.put(key + "Public", u.publicUsage());
        o.put(key + "HasMain", u.hasMain());
        o.put(key + "MainMeters", u.mainMeters());
        o.put(key + "MonthsWithoutMain", u.monthsWithoutMain());
    }

    @Operation(summary = "近N月用量/费用趋势(按能源类型,逐月总表口径,缺月补0)")
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

        // 逐月各自定口径:该月有总表读数用总表,没有退分表合计(与概览同一套 combine)
        String filter = "r.read_time >= DATE_SUB(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL ? MONTH)";
        fill(electric, electricFee, idx, monthlyUsage(TYPE_ELECTRIC, filter, months - 1));
        fill(water, waterFee, idx, monthlyUsage(TYPE_WATER, filter, months - 1));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("months", monthKeys);
        m.put("electric", Arrays.asList(electric));
        m.put("water", Arrays.asList(water));
        m.put("electricFee", Arrays.asList(electricFee));
        m.put("waterFee", Arrays.asList(waterFee));
        return Result.ok(m);
    }

    private static void fill(BigDecimal[] usage, BigDecimal[] fee, Map<String, Integer> idx,
                             Map<String, Usage> byMonth) {
        byMonth.forEach((ym, u) -> {
            Integer i = idx.get(ym);
            if (i != null) {
                usage[i] = u.total();
                fee[i] = u.fee();
            }
        });
    }

    @Operation(summary = "当月各分表用量排行 Top10(总表/参考表不参与排行)")
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
                  AND mt.meter_role IN ('TENANT', 'PROPERTY')
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
