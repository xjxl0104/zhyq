package com.zhyq.park.property;

import com.zhyq.park.property.service.SlaCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaCalculatorTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 15, 10, 0, 0);

    @Test
    void nullThreshold_isNotTimedOut() {
        assertFalse(SlaCalculator.isTimedOut(START, START.plusMinutes(100), null));
    }

    @Test
    void nullStart_isNotTimedOut() {
        assertFalse(SlaCalculator.isTimedOut(null, START, 10));
    }

    @Test
    void exactlyAtThreshold_isNotTimedOut() {
        // strictly-greater semantics: exactly at threshold is NOT yet timed out
        assertFalse(SlaCalculator.isTimedOut(START, START.plusMinutes(10), 10));
    }

    @Test
    void pastThreshold_isTimedOut() {
        assertTrue(SlaCalculator.isTimedOut(START, START.plusMinutes(11), 10));
    }

    @Test
    void withinThreshold_isNotTimedOut() {
        assertFalse(SlaCalculator.isTimedOut(START, START.plusMinutes(5), 10));
    }
}
