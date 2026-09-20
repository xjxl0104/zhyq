package com.zhyq.park.marketing.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 佣金池计算(PARK-MKT-001 §2.7a):租赁 = 单价 × 面积 × 佣金月数;入仓/直签 = 基数 × 总比例%。 */
class PoolCalculatorTest {

    @Test
    void leasePoolIsRentTimesAreaTimesMonths() {
        // A 级:300 元/㎡/月 × 100 ㎡ = 月租 30000 × 1 个月
        assertThat(PoolCalculator.leasePool(bd("300"), bd("100"), bd("1")))
                .isEqualByComparingTo("30000.00");
        // C 级:半个月
        assertThat(PoolCalculator.leasePool(bd("300"), bd("100"), bd("0.5")))
                .isEqualByComparingTo("15000.00");
    }

    @Test
    void leasePoolRoundsHalfUpToTwoDecimals() {
        // 33.333 × 10 × 0.25 = 83.3325 → 83.33
        assertThat(PoolCalculator.leasePool(bd("33.333"), bd("10"), bd("0.25")))
                .isEqualByComparingTo("83.33");
        assertThat(PoolCalculator.leasePool(bd("33.333"), bd("10"), bd("0.25")).scale()).isEqualTo(2);
    }

    @Test
    void ratePoolIsBaseTimesPercent() {
        // 出库单服务费 12.5 × 5% = 0.625 → 0.63
        assertThat(PoolCalculator.ratePool(bd("12.5"), bd("5"))).isEqualByComparingTo("0.63");
        // 直签平台费 12300 × 6%
        assertThat(PoolCalculator.ratePool(bd("12300"), bd("6"))).isEqualByComparingTo("738.00");
    }

    @Test
    void zeroInputsGiveZeroPool() {
        assertThat(PoolCalculator.leasePool(bd("0"), bd("100"), bd("1"))).isEqualByComparingTo("0.00");
        assertThat(PoolCalculator.ratePool(bd("100"), bd("0"))).isEqualByComparingTo("0.00");
    }

    @Test
    void nullOrNegativeInputsAreRejected() {
        assertThatThrownBy(() -> PoolCalculator.leasePool(null, bd("1"), bd("1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PoolCalculator.ratePool(bd("-1"), bd("5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PoolCalculator.ratePool(bd("1"), bd("101")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
