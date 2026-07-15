package com.zhyq.park.acc.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 停车费计算纯逻辑(#21)。不依赖任何 Spring/DB 上下文,便于单测(TDD)。
 *
 * <p>语义:首 {@code freeMinutes} 分钟免费,超出部分按小时向上取整 × {@code ratePerHour};
 * 若 {@code dailyCap} 非空,则按停放跨越的天数(ceil 总时长/24h)对总额封顶。
 * <ul>
 *   <li>enter 或 leave 为空 → 返回 null(在场/未知,不计费)。</li>
 *   <li>leave <= enter → 0.00(非法/零时长,由上层做状态校验)。</li>
 *   <li>停放在免费时长内 → 0.00。</li>
 *   <li>计费时长不足 1 小时按 1 小时计。</li>
 * </ul>
 * <b>本类只返回金额。上层将其挂在 acc_parking 记录上,绝不写 fin_bill / 触发收款。</b></p>
 */
public final class ParkingFeeCalculator {

    private ParkingFeeCalculator() {}

    /** 默认计费规则:首 2 小时免费,之后 ¥5/h,单日封顶 ¥30。 */
    public static final ParkingRule DEFAULT_RULE =
            new ParkingRule(120, new BigDecimal("5.00"), new BigDecimal("30.00"));

    /**
     * 计费规则(值对象,不可变)。
     *
     * @param freeMinutes 免费时长(分钟),<=0 表示无免费
     * @param ratePerHour 每小时费率(超出免费时长后)
     * @param dailyCap    单日封顶金额,可空(空则不封顶)
     */
    public record ParkingRule(int freeMinutes, BigDecimal ratePerHour, BigDecimal dailyCap) {

        /** 人类可读的计费说明,存 acc_parking.fee_rule 供前端展示。 */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (freeMinutes > 0) {
                sb.append("首").append(freeMinutes / 60.0 == freeMinutes / 60
                        ? (freeMinutes / 60) + "h" : freeMinutes + "min").append("免费,");
            }
            sb.append("¥").append(ratePerHour == null ? "0" : ratePerHour.stripTrailingZeros().toPlainString())
              .append("/h");
            if (dailyCap != null) {
                sb.append(",单日封顶¥").append(dailyCap.stripTrailingZeros().toPlainString());
            }
            return sb.toString();
        }
    }

    /**
     * 计算停车费。
     *
     * @param enterTime 入场时间,可空
     * @param leaveTime 出场时间,可空
     * @param rule      计费规则(为空则用 {@link #DEFAULT_RULE})
     * @return 费用金额(scale=2);enter/leave 任一为空则返回 null
     */
    public static BigDecimal calc(LocalDateTime enterTime, LocalDateTime leaveTime, ParkingRule rule) {
        if (enterTime == null || leaveTime == null) {
            return null;
        }
        ParkingRule r = (rule == null) ? DEFAULT_RULE : rule;

        long totalMinutes = Duration.between(enterTime, leaveTime).toMinutes();
        if (totalMinutes <= 0) {
            return zero();
        }

        long free = Math.max(r.freeMinutes(), 0);
        long billableMinutes = totalMinutes - free;
        if (billableMinutes <= 0) {
            return zero();
        }

        BigDecimal rate = (r.ratePerHour() == null) ? BigDecimal.ZERO : r.ratePerHour();
        long billableHours = (billableMinutes + 59) / 60; // ceil 到整小时
        BigDecimal fee = rate.multiply(BigDecimal.valueOf(billableHours));

        if (r.dailyCap() != null) {
            long days = (totalMinutes + (24 * 60 - 1)) / (24 * 60); // ceil 总时长/24h
            BigDecimal cap = r.dailyCap().multiply(BigDecimal.valueOf(Math.max(days, 1)));
            if (fee.compareTo(cap) > 0) {
                fee = cap;
            }
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
