package com.zhyq.park.marketing.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionEnginePoolInvariantTest {

    @Test
    void roundedSplitsNeverExceedPool() {
        List<Split> splits = CommissionEngine.split(new SplitRequest(
                new BigDecimal("0.01"),
                List.of(new ChainNode(1L, "P1", 1, false),
                        new ChainNode(2L, "P2", 1, false)),
                Map.of("P1", 50, "P2", 100)));

        BigDecimal total = splits.stream().map(Split::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isLessThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(splits).extracting(Split::amount)
                .containsExactly(new BigDecimal("0.01"), new BigDecimal("0.00"));
    }
}
