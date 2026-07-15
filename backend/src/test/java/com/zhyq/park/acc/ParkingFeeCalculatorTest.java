package com.zhyq.park.acc;

import com.zhyq.park.acc.service.ParkingFeeCalculator;
import com.zhyq.park.acc.service.ParkingFeeCalculator.ParkingRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ParkingFeeCalculator.calc 纯函数单测(TDD,#21)。
 *
 * <p>语义:首 N 分钟免费,超出部分按小时向上取整 × 费率;可选按天封顶。
 * <ul>
 *   <li>enter 或 leave 为空 → null(在场/未知,不计费)。</li>
 *   <li>leave <= enter → 0.00(非法/零时长)。</li>
 *   <li>免费时长内 → 0.00。</li>
 *   <li>超出免费时长:不足 1 小时按 1 小时计。</li>
 *   <li>dailyCap 非空:按跨越的天数(ceil 总时长/24h)封顶。</li>
 * </ul>
 * <b>纯金额计算,绝不触碰任何财务表。</b></p>
 */
class ParkingFeeCalculatorTest {

    // 首 2 小时免费,之后 ¥5/h,无封顶
    private static final ParkingRule FREE2_RATE5 =
            new ParkingRule(120, new BigDecimal("5.00"), null);
    // 首 0 免费,¥5/h,单日封顶 ¥30
    private static final ParkingRule RATE5_CAP30 =
            new ParkingRule(0, new BigDecimal("5.00"), new BigDecimal("30.00"));

    private static LocalDateTime at(int h, int m) {
        return LocalDateTime.of(2026, 7, 15, h, m, 0);
    }

    @Test
    void nullEnter_isNull() {
        assertNull(ParkingFeeCalculator.calc(null, at(10, 0), FREE2_RATE5));
    }

    @Test
    void nullLeave_isNull_stillParked() {
        assertNull(ParkingFeeCalculator.calc(at(10, 0), null, FREE2_RATE5));
    }

    @Test
    void leaveBeforeEnter_isZero() {
        BigDecimal fee = ParkingFeeCalculator.calc(at(12, 0), at(10, 0), FREE2_RATE5);
        assertEquals(0, new BigDecimal("0.00").compareTo(fee));
    }

    @Test
    void withinFreeWindow_isZero() {
        // 90min < 120min 免费 → 0
        BigDecimal fee = ParkingFeeCalculator.calc(at(10, 0), at(11, 30), FREE2_RATE5);
        assertEquals(0, new BigDecimal("0.00").compareTo(fee));
    }

    @Test
    void exactlyFreeWindow_isZero() {
        // 恰好 120min → 0
        BigDecimal fee = ParkingFeeCalculator.calc(at(10, 0), at(12, 0), FREE2_RATE5);
        assertEquals(0, new BigDecimal("0.00").compareTo(fee));
    }

    @Test
    void oneMinuteOverFree_chargesOneHour() {
        // 121min:超出 1min → ceil 1h → 5.00
        BigDecimal fee = ParkingFeeCalculator.calc(at(10, 0), at(12, 1), FREE2_RATE5);
        assertEquals(0, new BigDecimal("5.00").compareTo(fee));
    }

    @Test
    void threeHoursOverFree_ceilsHours() {
        // 停 4h,免费 2h,计费 2h → 10.00
        BigDecimal fee = ParkingFeeCalculator.calc(at(10, 0), at(14, 0), FREE2_RATE5);
        assertEquals(0, new BigDecimal("10.00").compareTo(fee));
    }

    @Test
    void partialBillableHour_ceils() {
        // 停 3h30m,免费 2h,计费 1h30m → ceil 2h → 10.00
        BigDecimal fee = ParkingFeeCalculator.calc(at(10, 0), at(13, 30), FREE2_RATE5);
        assertEquals(0, new BigDecimal("10.00").compareTo(fee));
    }

    @Test
    void dailyCapApplied_singleDay() {
        // 10h × 5 = 50,单日封顶 30 → 30.00
        BigDecimal fee = ParkingFeeCalculator.calc(at(0, 0), at(10, 0), RATE5_CAP30);
        assertEquals(0, new BigDecimal("30.00").compareTo(fee));
    }

    @Test
    void dailyCapApplied_multiDay() {
        // 26h:跨 2 天(ceil 26/24=2)→ 封顶 2×30=60;raw=26×5=130 → 60.00
        BigDecimal fee = ParkingFeeCalculator.calc(
                LocalDateTime.of(2026, 7, 15, 0, 0),
                LocalDateTime.of(2026, 7, 16, 2, 0), RATE5_CAP30);
        assertEquals(0, new BigDecimal("60.00").compareTo(fee));
    }

    @Test
    void belowCap_notCapped() {
        // 3h × 5 = 15 < 30 → 15.00
        BigDecimal fee = ParkingFeeCalculator.calc(at(0, 0), at(3, 0), RATE5_CAP30);
        assertEquals(0, new BigDecimal("15.00").compareTo(fee));
    }

    @Test
    void feeScaleIsTwoDecimals() {
        assertEquals(2, ParkingFeeCalculator.calc(at(10, 0), at(14, 0), FREE2_RATE5).scale());
    }

    @Test
    void defaultRule_describeIsHumanReadable() {
        String desc = ParkingFeeCalculator.DEFAULT_RULE.describe();
        // 默认:首2h免费,¥5/h
        org.junit.jupiter.api.Assertions.assertNotNull(desc);
        org.junit.jupiter.api.Assertions.assertTrue(desc.contains("免费"));
    }
}
