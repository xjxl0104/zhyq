package com.zhyq.park.marketing.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 佣金池计算(PARK-MKT-001 §2.7a 两套产品):
 * <ul>
 *   <li>园区入驻(租赁):池 = 单价(元/㎡/月) × 面积 × 佣金月数(0.25–2,按评级),一次性;</li>
 *   <li>客户入仓 · 园区签:池 = 该出库单的园区服务费 × 评级总比例%,每单;</li>
 *   <li>客户入仓 · 云仓直签:池 = 每期平台费 × 评级总比例%。</li>
 * </ul>
 * 池子算好后统一交给 {@link CommissionEngine} 做级差拆分。金额 HALF_UP 2 位。
 */
public final class PoolCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PoolCalculator() {
    }

    /** 租赁:单价 × 面积 × 佣金月数。 */
    public static BigDecimal leasePool(BigDecimal rentPrice, BigDecimal rentArea, BigDecimal commissionMonths) {
        requireNonNegative(rentPrice, "rentPrice");
        requireNonNegative(rentArea, "rentArea");
        requireNonNegative(commissionMonths, "commissionMonths");
        return rentPrice.multiply(rentArea).multiply(commissionMonths).setScale(2, RoundingMode.HALF_UP);
    }

    /** 入仓 / 直签:基数 × 总比例%(0–100)。 */
    public static BigDecimal ratePool(BigDecimal base, BigDecimal totalRatePct) {
        requireNonNegative(base, "base");
        requireNonNegative(totalRatePct, "totalRatePct");
        if (totalRatePct.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("totalRatePct 不能超过 100: " + totalRatePct);
        }
        return base.multiply(totalRatePct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static void requireNonNegative(BigDecimal v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        if (v.signum() < 0) {
            throw new IllegalArgumentException(name + " 不能为负: " + v);
        }
    }
}
