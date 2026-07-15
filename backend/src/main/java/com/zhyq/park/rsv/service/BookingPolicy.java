package com.zhyq.park.rsv.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 预订计费纯逻辑(#23)。不依赖任何 Spring/DB 上下文,便于单测。
 *
 * <p>语义:fee = ceil(时长小时) × pricePerHour。
 * <ul>
 *   <li>pricePerHour 为空 → 返回 null(该资源不计费)。</li>
 *   <li>时长 <= 0 → 返回 0.00(非法/零时长不计费,由上层做时段校验)。</li>
 *   <li>不足 1 小时按 1 小时计,超过整点向上取整到下一小时(如 90min → 2h)。</li>
 * </ul>
 * <b>费用只算金额,由上层挂在预订记录上,绝不写 fin_bill / 触发收款。</b></p>
 */
public final class BookingPolicy {

    private BookingPolicy() {}

    /**
     * @param minutes      预订时长(分钟)
     * @param pricePerHour 每小时单价,可空
     * @return 费用金额(scale=2),price 为空则返回 null
     */
    public static BigDecimal calcFee(long minutes, BigDecimal pricePerHour) {
        if (pricePerHour == null) {
            return null;
        }
        if (minutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long hours = (minutes + 59) / 60; // ceil to whole hours
        return pricePerHour.multiply(BigDecimal.valueOf(hours)).setScale(2, RoundingMode.HALF_UP);
    }
}
