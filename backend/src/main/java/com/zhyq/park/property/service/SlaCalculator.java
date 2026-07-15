package com.zhyq.park.property.service;

import java.time.LocalDateTime;

/**
 * SLA 超时纯逻辑判断(#10)。不依赖任何 Spring/DB 上下文,便于单测。
 *
 * <p>语义:thresholdMinutes 非空且 start 非空,且 now 严格晚于 start+thresholdMinutes 分钟才算超时;
 * 刚好到达阈值(now == start+threshold)不算超时,须再过 1 毫秒才算。</p>
 */
public final class SlaCalculator {

    private SlaCalculator() {}

    public static boolean isTimedOut(LocalDateTime start, LocalDateTime now, Integer thresholdMinutes) {
        if (thresholdMinutes == null || start == null || now == null) {
            return false;
        }
        LocalDateTime deadline = start.plusMinutes(thresholdMinutes);
        return now.isAfter(deadline);
    }
}
