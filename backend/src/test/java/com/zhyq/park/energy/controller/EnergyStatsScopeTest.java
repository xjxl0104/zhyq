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
 * 有总表时拆 租户 / 物业 / 公摊;没总表退分表合计;参考表不进统计;费用只按分表算。
 *
 * <p>取自园区 2026-08 真实抄表:大厦总表 1012 吨、21 块租户分表合计 562.5 吨、
 * 另有一块「园区总表 SB-4475」710 吨属参考表。修前概览把三者相加成 2284.5,
 * 负责人给的正确口径是 总 1012 / 租户 562.5 / 公摊 449.5。</p>
 */
class EnergyStatsScopeTest {

    private static Map<String, Object> row(String role, long cnt, String usage, String fee) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("meter_role", role);
        m.put("cnt", cnt);
        m.put("usage_amount", new BigDecimal(usage));
        m.put("fee", new BigDecimal(fee));
        return m;
    }

    @Test
    @DisplayName("8月水:总表 1012、分表 562.5、参考表 710 → 总 1012 / 租户 562.5 / 公摊 449.5,参考表不计")
    void waterAugustUsesMainMeterAndSplits() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("MAIN", 1, "1012.00", "0"),
                row("TENANT", 21, "562.50", "0"),
                row("REFERENCE", 1, "710.00", "0")));
        assertTrue(u.hasMain());
        assertEquals(0, new BigDecimal("1012.00").compareTo(u.total()));
        assertEquals(0, new BigDecimal("562.50").compareTo(u.tenant()));
        assertEquals(0, BigDecimal.ZERO.compareTo(u.property()));
        assertEquals(0, new BigDecimal("449.50").compareTo(u.publicUsage()));
    }

    @Test
    @DisplayName("没有总表读数(园区电表未装总表):退回分表合计,公摊为空")
    void withoutMainFallsBackToSubmeters() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("TENANT", 23, "128598.00", "0")));
        assertFalse(u.hasMain());
        assertEquals(0, new BigDecimal("128598.00").compareTo(u.total()));
        assertNull(u.publicUsage());
    }

    @Test
    @DisplayName("物业公司表计入分母:公摊 = 总表 − 租户 − 物业")
    void propertyMeterReducesPublicUsage() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("MAIN", 1, "1000", "0"),
                row("TENANT", 5, "600", "0"),
                row("PROPERTY", 1, "100", "0")));
        assertEquals(0, new BigDecimal("1000").compareTo(u.total()));
        assertEquals(0, new BigDecimal("100").compareTo(u.property()));
        assertEquals(0, new BigDecimal("300").compareTo(u.publicUsage()));
    }

    @Test
    @DisplayName("费用只按分表算:总表本身不出账,参考表也不算钱")
    void feeCountsSubmetersOnly() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of(
                row("MAIN", 1, "1000", "999"),
                row("TENANT", 2, "600", "120.50"),
                row("PROPERTY", 1, "100", "30"),
                row("REFERENCE", 1, "50", "77")));
        assertEquals(0, new BigDecimal("150.50").compareTo(u.fee()));
    }

    @Test
    @DisplayName("空窗口(今日无抄表):全 0、无总表、公摊为空")
    void emptyWindow() {
        EnergyStatsController.Usage u = EnergyStatsController.combine(List.of());
        assertFalse(u.hasMain());
        assertEquals(0, BigDecimal.ZERO.compareTo(u.total()));
        assertNull(u.publicUsage());
    }
}
