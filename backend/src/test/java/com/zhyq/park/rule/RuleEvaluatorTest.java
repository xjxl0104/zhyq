package com.zhyq.park.rule;

import com.zhyq.park.common.event.DomainEvent.AlarmRaised;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEvaluatorTest {

    private AlarmRaised alarm(String level) {
        return new AlarmRaised(1L, 2L, level, 3L, LocalDateTime.now());
    }

    @Test
    void emptyCondition_matchesAll() {
        assertTrue(RuleEvaluator.matches("{}", alarm("1")));
    }

    @Test
    void nullCondition_matchesAll() {
        assertTrue(RuleEvaluator.matches(null, alarm("1")));
    }

    @Test
    void blankCondition_matchesAll() {
        assertTrue(RuleEvaluator.matches("  ", alarm("1")));
    }

    @Test
    void minLevel_matches_whenEventLevelHigher() {
        assertTrue(RuleEvaluator.matches("{\"minLevel\":2}", alarm("3")));
    }

    @Test
    void minLevel_matches_whenEventLevelEqual() {
        assertTrue(RuleEvaluator.matches("{\"minLevel\":2}", alarm("2")));
    }

    @Test
    void minLevel_rejects_whenEventLevelLower() {
        assertFalse(RuleEvaluator.matches("{\"minLevel\":2}", alarm("1")));
    }

    @Test
    void malformedJson_isNoMatch() {
        assertFalse(RuleEvaluator.matches("{not-json", alarm("9")));
    }
}
