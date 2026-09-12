package com.zhyq.park.energy.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 能耗统计口径回归锁(2026-09-12):总用量 = 总表读数,不把总表和分表相加;
 * 总表分表同期都有读数时拆 租户 / 物业 / 公摊;没总表退分表合计;参考表不进统计;费用只按分表算;
 * 跨月窗口逐月合成,缺总表的月份不会让别的月的公摊算成负数。
 *
 * <p>取自园区 2026-08 真实抄表:大厦总表 1012 吨、21 块租户分表合计 562.5 吨、
 * 另有一块「园区总表 SB-4475」710 吨属参考表。修前概览把三者相加成 2284.5,
 * 负责人给的正确口径是 总 1012 / 租户 562.5 / 公摊 449.5。</p>
 */
class EnergyStatsScopeTest {

    private static Map<String, Object> row(String role, long meters, String usage, String fee) {
        return row("2026-08", role, meters, usage, fee);
    }

    private static Map<String, Object> row(String ym, String role, long meters, String usage, String fee) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ym", ym);
        m.put("meter_role", role);
        m.put("meters", meters);
        m.put("usage_amount", new BigDecimal(usage));
        m.put("fee", new BigDecimal(fee));
        return m;
    }

    private static void assertNum(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " got " + actual);
    }

    @Test
    @DisplayName("8月水:总表 1012、分表 562.5、参考表 710 → 总 1012 / 租户 562.5 / 公摊 449.5,参考表不计")
    void waterAugustUsesMainMeterAndSplits() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("MAIN", 1, "1012.00", "0"),
                row("TENANT", 21, "562.50", "0"),
                row("REFERENCE", 1, "710.00", "0")));
        assertTrue(u.hasMain());
        assertEquals(1, u.mainMeters());
        assertNum("1012.00", u.total());
        assertNum("562.50", u.tenant());
        assertNum("0", u.property());
        assertNum("449.50", u.publicUsage());
    }

    @Test
    @DisplayName("没有总表读数(园区电表未装总表):退回分表合计,公摊为空")
    void withoutMainFallsBackToSubmeters() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("TENANT", 23, "128598.00", "0")));
        assertFalse(u.hasMain());
        assertNum("128598.00", u.total());
        assertNull(u.publicUsage());
    }

    @Test
    @DisplayName("有总表但分表没抄:总量给总表,不拆公摊(否则公摊=总量是假数)")
    void mainWithoutSubmetersDoesNotSplit() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("MAIN", 1, "1012", "0")));
        assertTrue(u.hasMain());
        assertNum("1012", u.total());
        assertNull(u.publicUsage());
    }

    @Test
    @DisplayName("两块 MAIN 同期都有读数:mainMeters=2 带回前端提示,用量按两块相加")
    void twoMainMetersAreReported() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("MAIN", 2, "1722", "0"),
                row("TENANT", 21, "562.5", "0")));
        assertEquals(2, u.mainMeters());
        assertNum("1722", u.total());
    }

    @Test
    @DisplayName("物业公司表计入分母:公摊 = 总表 − 租户 − 物业")
    void propertyMeterReducesPublicUsage() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("MAIN", 1, "1000", "0"),
                row("TENANT", 5, "600", "0"),
                row("PROPERTY", 1, "100", "0")));
        assertNum("1000", u.total());
        assertNum("100", u.property());
        assertNum("300", u.publicUsage());
    }

    @Test
    @DisplayName("费用只按分表算:总表本身不出账,参考表也不算钱")
    void feeCountsSubmetersOnly() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("MAIN", 1, "1000", "999"),
                row("TENANT", 2, "600", "120.50"),
                row("PROPERTY", 1, "100", "30"),
                row("REFERENCE", 1, "50", "77")));
        assertNum("150.50", u.fee());
    }

    @Test
    @DisplayName("跨月窗口逐月合成:8月有总表、9月只有分表 → 总量相加、公摊只算8月、标 1 个月无总表")
    void yearWindowSumsMonthsWithoutMixingScopes() {
        Map<String, EnergyStatsController.Usage> byMonth = EnergyStatsController.monthly(List.of(
                row("2026-09", "TENANT", 21, "300", "0"),
                row("2026-08", "MAIN", 1, "1012", "0"),
                row("2026-08", "TENANT", 21, "562.5", "0")));
        assertEquals(List.of("2026-08", "2026-09"), List.copyOf(byMonth.keySet()));

        EnergyStatsController.Usage year = EnergyStatsController.sumMonths(byMonth.values());
        assertTrue(year.hasMain());
        assertNum("1312", year.total());        // 1012(总表口径) + 300(分表合计)
        assertNum("862.5", year.tenant());
        assertNum("449.5", year.publicUsage()); // 不是 1012 − 862.5 = 149.5
        assertEquals(1, year.monthsWithoutMain());
    }

    @Test
    @DisplayName("空窗口(今日无抄表):全 0、无总表、公摊为空")
    void emptyWindow() {
        EnergyStatsController.Usage u = EnergyStatsController.sumMonths(List.of());
        assertFalse(u.hasMain());
        assertNum("0", u.total());
        assertNull(u.publicUsage());
        assertEquals(0, u.monthsWithoutMain());
    }

    @Test
    @DisplayName("接口键名钉死:前端 Stats.vue 按 year/yearFee/yearTenant/yearProperty/yearPublic/yearHasMain/yearMainMeters/yearMonthsWithoutMain 取值")
    void windowKeysAreStable() {
        Map<String, Object> o = new LinkedHashMap<>();
        EnergyStatsController.putWindow(o, "year", EnergyStatsController.combine(List.of(
                row("MAIN", 1, "1012", "0"), row("TENANT", 21, "562.5", "12"))));
        assertEquals(List.of("year", "yearFee", "yearTenant", "yearProperty", "yearPublic",
                "yearHasMain", "yearMainMeters", "yearMonthsWithoutMain"), List.copyOf(o.keySet()));
        assertNum("1012", (BigDecimal) o.get("year"));
        assertNum("12", (BigDecimal) o.get("yearFee"));
        assertEquals(Boolean.TRUE, o.get("yearHasMain"));
    }
}
