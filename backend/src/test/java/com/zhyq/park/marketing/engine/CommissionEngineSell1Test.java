package com.zhyq.park.marketing.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 级差引擎(PARK-MKT-001 §2.7 / §2.9 / §2.13)。sell1 自写的一组;sell2 的 CommissionEngineTest 是独立第二组。
 */
class CommissionEngineSell1Test {

    private static final Map<String, Integer> LADDER2 = ladder("P1", 60, "P2", 100);
    private static final Map<String, Integer> LADDER4 = ladder("P1", 50, "P2", 70, "P3", 85, "P4", 100);

    @Test
    void fourLevelChainSplitsPoolByDiff() {
        // §2.7a 算例:池 30000,链 P1→P2→P3→P4 → 15000 / 6000 / 4500 / 4500
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("30000"),
                List.of(node(1, "P1"), node(2, "P2"), node(3, "P3"), node(4, "P4")), LADDER4));
        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(out).extracting(Split::diffPct).containsExactly(50, 20, 15, 15);
        assertThat(out).extracting(s -> s.amount().toPlainString())
                .containsExactly("15000.00", "6000.00", "4500.00", "4500.00");
        assertThat(sum(out)).isEqualByComparingTo("30000.00");
    }

    @Test
    void twoLevelChain() {
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("1000"),
                List.of(node(1, "P1"), node(2, "P2")), LADDER2));
        assertThat(out).extracting(Split::diffPct).containsExactly(60, 40);
        assertThat(sum(out)).isEqualByComparingTo("1000.00");
    }

    @Test
    void sellerAtTopTakesEverything() {
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("1000"),
                List.of(node(4, "P4"), node(9, "P4")), LADDER4));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).promoterId()).isEqualTo(4L);
        assertThat(out.get(0).amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void sameLevelUplineIsCompressed() {
        // P1 → P1 → P2:中间那个 P1 份额 50 不高于已拿 50,跳过;P2 拿 20
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("1000"),
                List.of(node(1, "P1"), node(2, "P1"), node(3, "P2")), LADDER4));
        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 3L);
        assertThat(out).extracting(Split::diffPct).containsExactly(50, 20);
    }

    @Test
    void invertedUplineIsCompressed() {
        // 成交人是 P3(85),上级只是 P1(50):上级拿不到;再上级 P4 拿 15
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("1000"),
                List.of(node(1, "P3"), node(2, "P1"), node(3, "P4")), LADDER4));
        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 3L);
        assertThat(out).extracting(Split::diffPct).containsExactly(85, 15);
    }

    @Test
    void chainEndingBelowTopLeavesRemainderToPark() {
        // 只有 P1,没有上级:拿 50%,剩 50% 园区留存(不出流水)
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("1000"),
                List.of(node(1, "P1")), LADDER4));
        assertThat(out).hasSize(1);
        assertThat(sum(out)).isEqualByComparingTo("500.00");
    }

    @Test
    void exitedNodeShareIsRetainedByParkNotPassedUp() {
        // P1(正常) → P2(已退出) → P3:P2 的 20 园区留存,P3 只拿 15
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("1000"),
                List.of(node(1, "P1"), new ChainNode(2L, "P2", 4, false), node(3, "P3")), LADDER4));
        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 3L);
        assertThat(out).extracting(Split::diffPct).containsExactly(50, 15);
        assertThat(sum(out)).isEqualByComparingTo("650.00");
    }

    @Test
    void internalNodeShareIsRetainedByPark() {
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("1000"),
                List.of(new ChainNode(1L, "P1", 1, true), node(2, "P2")), LADDER4));
        assertThat(out).extracting(Split::promoterId).containsExactly(2L);
        assertThat(out.get(0).diffPct()).isEqualTo(20);
    }

    @Test
    void frozenNodeStillGetsCommission() {
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("1000"),
                List.of(new ChainNode(1L, "P1", 2, false), node(2, "P2")), LADDER4));
        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 2L);
    }

    @Test
    void overrideLadderIsHonoured() {
        // 钻石合伙人把 P1 下调到 40
        Map<String, Integer> ladder = ladder("P1", 40, "P2", 70, "P3", 85, "P4", 100);
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("1000"),
                List.of(node(1, "P1"), node(2, "P2")), ladder));
        assertThat(out).extracting(Split::diffPct).containsExactly(40, 30);
    }

    @Test
    void amountsRoundHalfUpAndNeverExceedPool() {
        // 池 0.01,P1 50% = 0.005 → 0.01;P2 20% = 0.002 → 0.00;总和 0.01 不超池
        List<Split> out = CommissionEngine.split(new SplitRequest(bd("0.01"),
                List.of(node(1, "P1"), node(2, "P2")), LADDER4));
        assertThat(out.get(0).amount()).isEqualByComparingTo("0.01");
        assertThat(out.get(1).amount()).isEqualByComparingTo("0.00");
        assertThat(sum(out)).isLessThanOrEqualTo(bd("0.01"));
        assertThat(out.get(0).amount().scale()).isEqualTo(2);
    }

    @Test
    void zeroPoolGivesNoSplits() {
        assertThat(CommissionEngine.split(new SplitRequest(bd("0"), List.of(node(1, "P1")), LADDER4))).isEmpty();
    }

    @Test
    void duplicatePayeeIsRejected() {
        assertThatThrownBy(() -> CommissionEngine.split(new SplitRequest(bd("100"),
                List.of(node(1, "P1"), node(1, "P2")), LADDER4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingPositionInLadderIsRejected() {
        assertThatThrownBy(() -> CommissionEngine.split(new SplitRequest(bd("100"),
                List.of(node(1, "P1"), node(2, "P9")), LADDER4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyChainIsRejected() {
        assertThatThrownBy(() -> CommissionEngine.split(new SplitRequest(bd("100"), List.of(), LADDER4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ChainNode node(long id, String code) {
        return new ChainNode(id, code, 1, false);
    }

    private static Map<String, Integer> ladder(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    private static BigDecimal sum(List<Split> out) {
        return out.stream().map(Split::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
