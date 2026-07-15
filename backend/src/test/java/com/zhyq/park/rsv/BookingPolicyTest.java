package com.zhyq.park.rsv;

import com.zhyq.park.rsv.service.BookingPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BookingPolicy.calcFee 纯函数单测(TDD)。
 * 语义:fee = ceil(时长小时) × price_per_hour;price 为空 → fee 为空;时长 <= 0 → 0.00。
 */
class BookingPolicyTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Test
    void ninetyMinutesAtTen_ceilsToTwoHours_isTwenty() {
        // 90min = 1.5h → ceil = 2h → 2 * 10.00 = 20.00
        assertEquals(0, new BigDecimal("20.00").compareTo(BookingPolicy.calcFee(90, TEN)));
    }

    @Test
    void exactlyOneHour_isOnePrice() {
        assertEquals(0, new BigDecimal("10.00").compareTo(BookingPolicy.calcFee(60, TEN)));
    }

    @Test
    void oneMinuteOverAnHour_ceilsToTwoHours() {
        assertEquals(0, new BigDecimal("20.00").compareTo(BookingPolicy.calcFee(61, TEN)));
    }

    @Test
    void oneMinute_ceilsToOneHour() {
        assertEquals(0, new BigDecimal("10.00").compareTo(BookingPolicy.calcFee(1, TEN)));
    }

    @Test
    void nullPrice_isNullFee() {
        assertNull(BookingPolicy.calcFee(90, null));
    }

    @Test
    void zeroDuration_isZeroFee() {
        assertEquals(0, BigDecimal.ZERO.compareTo(BookingPolicy.calcFee(0, TEN)));
    }

    @Test
    void negativeDuration_isZeroFee() {
        assertEquals(0, BigDecimal.ZERO.compareTo(BookingPolicy.calcFee(-30, TEN)));
    }

    @Test
    void feeScaleIsTwoDecimals() {
        assertEquals(2, BookingPolicy.calcFee(60, TEN).scale());
    }
}
