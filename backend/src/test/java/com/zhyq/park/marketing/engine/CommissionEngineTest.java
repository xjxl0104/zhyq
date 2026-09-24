package com.zhyq.park.marketing.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 级差引擎第二组用例(sell2,独立于 {@link CommissionEngineSell1Test})。
 *
 * <p>规则来源 PARK-MKT-001 §2.7(级差拆分)/ §2.7a(租赁算例)/ §2.9(冻结照发、退出留存)/
 * §2.12(钻石 override 只降不升)/ §2.13(内部人员不计佣)。
 * 除了把每条规则各钉一个用例,这组重点放在引擎对外承诺的三条不变量:
 * {@code sum(amount) ≤ pool}、无重复收款人、份额/级差快照与阶梯一致。</p>
 */
class CommissionEngineTest {

    private static final int NORMAL = 1;
    private static final int FROZEN = 2;
    private static final int PENDING = 3;
    private static final int EXITED = 4;

    private static final Map<String, Integer> LADDER2 = ladder("P1", 60, "P2", 100);
    private static final Map<String, Integer> LADDER4 = ladder("P1", 50, "P2", 70, "P3", 85, "P4", 100);

    // ---------- §2.7 基本拆分 ----------

    @Test
    @DisplayName("2 级阶梯:成交人 60%,上级拿级差 40%,合计等于池")
    void twoLevelLadder() {
        List<Split> out = split("5000", LADDER2, node(11, "P1"), node(12, "P2"));

        assertThat(out).extracting(Split::promoterId).containsExactly(11L, 12L);
        assertThat(out).extracting(Split::sharePct).containsExactly(60, 100);
        assertThat(out).extracting(Split::diffPct).containsExactly(60, 40);
        assertThat(amounts(out)).containsExactly("3000.00", "2000.00");
        assertThat(sum(out)).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("4 级阶梯 §2.7a 算例:30000 → 15000 / 6000 / 4500 / 4500")
    void fourLevelLadderSpecExample() {
        List<Split> out = split("30000", LADDER4,
                node(1, "P1"), node(2, "P2"), node(3, "P3"), node(4, "P4"));

        assertThat(amounts(out)).containsExactly("15000.00", "6000.00", "4500.00", "4500.00");
        assertThat(out).extracting(Split::diffPct).containsExactly(50, 20, 15, 15);
        assertThat(sum(out)).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("成交人自己就是顶格岗位:一人拿满 100%,链上其余人一分不拿")
    void sellerAtTopTakesWholePool() {
        List<Split> out = split("8000", LADDER4, node(9, "P4"), node(8, "P3"), node(7, "P4"));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).promoterId()).isEqualTo(9L);
        assertThat(out.get(0).diffPct()).isEqualTo(100);
        assertThat(out.get(0).amount()).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("链上同级(份额相等)被压缩跳过,级差留给更高一级")
    void sameLevelUplineCompressed() {
        List<Split> out = split("1000", LADDER4, node(1, "P2"), node(2, "P2"), node(3, "P3"));

        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 3L);
        assertThat(out).extracting(Split::diffPct).containsExactly(70, 15);
        assertThat(amounts(out)).containsExactly("700.00", "150.00");
    }

    @Test
    @DisplayName("链上倒挂(上级份额低于下级)被压缩,不出负数级差")
    void invertedUplineCompressed() {
        List<Split> out = split("1000", LADDER4, node(1, "P3"), node(2, "P1"), node(3, "P2"), node(4, "P4"));

        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 4L);
        assertThat(out).extracting(Split::diffPct).containsExactly(85, 15);
        assertThat(out).allSatisfy(s -> assertThat(s.diffPct()).isPositive());
    }

    @Test
    @DisplayName("2 级阶梯下三个节点 P1→P1→P2:中间的 P1 压缩,P2 仍只拿 40%")
    void twoLevelLadderWithRedundantMiddleNode() {
        List<Split> out = split("1000", LADDER2, node(1, "P1"), node(2, "P1"), node(3, "P2"));

        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 3L);
        assertThat(amounts(out)).containsExactly("600.00", "400.00");
    }

    // ---------- §2.12 钻石 override ----------

    @Test
    @DisplayName("钻石合伙人把 P1 从 50 下调到 40 后:成交人拿 40%,其上级级差相应变大")
    void overrideLoweredLadderShiftsDiffUpward() {
        Map<String, Integer> overridden = ladder("P1", 40, "P2", 70, "P3", 85, "P4", 100);

        List<Split> out = split("10000", overridden, node(1, "P1"), node(2, "P2"), node(3, "P4"));

        assertThat(out).extracting(Split::sharePct).containsExactly(40, 70, 100);
        assertThat(out).extracting(Split::diffPct).containsExactly(40, 30, 30);
        assertThat(amounts(out)).containsExactly("4000.00", "3000.00", "3000.00");
    }

    // ---------- §2.9 / §2.13 留存与冻结 ----------

    @Test
    @DisplayName("退出(4)的上级:其级差园区留存,不出流水,也不向更上级补")
    void exitedUplineShareRetainedByPark() {
        List<Split> out = split("10000", LADDER4,
                node(1, "P1"), node(2, "P2", EXITED), node(3, "P3"), node(4, "P4"));

        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 3L, 4L);
        assertThat(out).extracting(Split::diffPct).containsExactly(50, 15, 15);
        assertThat(sum(out)).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("成交人本人已退出:成交人那一档留存,上级仍只拿自己的级差")
    void exitedSellerShareRetainedNotGivenToUpline() {
        List<Split> out = split("10000", LADDER4, node(1, "P1", EXITED), node(2, "P2"));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).promoterId()).isEqualTo(2L);
        assertThat(out.get(0).diffPct()).isEqualTo(20);
        assertThat(out.get(0).amount()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("内部人员(命中 sys_user)不计佣:其级差留存,不向上补")
    void internalNodeShareRetainedByPark() {
        List<Split> out = split("10000", LADDER4,
                node(1, "P1"), node(2, "P2"), node(3, "P3", NORMAL, true), node(4, "P4"));

        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 2L, 4L);
        assertThat(out).extracting(Split::diffPct).containsExactly(50, 20, 15);
        assertThat(sum(out)).isEqualByComparingTo("8500");
    }

    @Test
    @DisplayName("整条链都是内部人员:不抛异常,返回空列表(全部园区留存)")
    void allInternalChainYieldsNothing() {
        List<Split> out = split("10000", LADDER2,
                node(1, "P1", NORMAL, true), node(2, "P2", NORMAL, true));

        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("退出节点如果本来就被压缩(份额 ≤ taken),对结果没有任何影响")
    void exitedButCompressedNodeIsNoop() {
        List<Split> withExited = split("1000", LADDER4, node(1, "P2"), node(2, "P1", EXITED), node(3, "P4"));
        List<Split> without = split("1000", LADDER4, node(1, "P2"), node(3, "P4"));

        assertThat(withExited).isEqualTo(without);
    }

    @Test
    @DisplayName("冻结(2)的节点照常出流水,金额与正常状态一致")
    void frozenNodeStillPaid() {
        List<Split> frozen = split("10000", LADDER4, node(1, "P1"), node(2, "P2", FROZEN), node(3, "P4"));
        List<Split> normal = split("10000", LADDER4, node(1, "P1"), node(2, "P2"), node(3, "P4"));

        assertThat(frozen).isEqualTo(normal);
        assertThat(frozen).extracting(Split::promoterId).contains(2L);
    }

    // ---------- 链断 / 边界 ----------

    @Test
    @DisplayName("链没走到 100%(上级为空):剩余部分园区留存,合计小于池")
    void brokenChainLeavesRemainderToPark() {
        List<Split> out = split("10000", LADDER4, node(1, "P1"), node(2, "P2"));

        assertThat(out).hasSize(2);
        assertThat(sum(out)).isEqualByComparingTo("7000");
    }

    @Test
    @DisplayName("到达 100% 后链上多余的节点被忽略,不会再出流水")
    void nodesAfterTopAreIgnored() {
        List<Split> out = split("1000", LADDER4, node(1, "P1"), node(2, "P4"), node(3, "P3"), node(4, "P4"));

        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 2L);
        assertThat(sum(out)).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("池为 0 或 null:返回空列表,不抛异常")
    void zeroOrNullPoolGivesNothing() {
        assertThat(split("0", LADDER4, node(1, "P1"), node(2, "P4"))).isEmpty();
        assertThat(CommissionEngine.split(new SplitRequest(null,
                List.of(node(1, "P1"), node(2, "P4")), LADDER4))).isEmpty();
    }

    @Test
    @DisplayName("金额固定 2 位小数,整数池也输出 xx.00")
    void amountScaleIsAlwaysTwo() {
        List<Split> out = split("30000", LADDER4, node(1, "P1"), node(2, "P4"));

        assertThat(out).allSatisfy(s -> assertThat(s.amount().scale()).isEqualTo(2));
        assertThat(amounts(out)).containsExactly("15000.00", "15000.00");
    }

    @Test
    @DisplayName("池带分厘时按 HALF_UP 取 2 位:1234.567 × 50% = 617.28,× 50% = 617.28")
    void fractionalPoolRoundsHalfUp() {
        List<Split> out = split("1234.567", LADDER4, node(1, "P1"), node(2, "P4"));

        assertThat(amounts(out)).containsExactly("617.28", "617.28");
    }

    @Test
    @DisplayName("不变量 sum(amount) ≤ pool:出库单级别的小池(如 1.25 元)逐分校验")
    void sumNeverExceedsPoolForSmallPools() {
        // 入仓发货的池 = 服务费 × 评级比例,常见量级只有几毛到几块钱(§2.7a 算例 12.5 × 5%)。
        // 每档单独 HALF_UP 的话,50/20/15/15 四档在 1.25 元时会拆出 0.63+0.25+0.19+0.19 = 1.26。
        List<String> violations = new ArrayList<>();
        for (int cents = 1; cents <= 500; cents++) {
            BigDecimal pool = BigDecimal.valueOf(cents, 2);
            List<Split> out = split(pool.toPlainString(), LADDER4,
                    node(1, "P1"), node(2, "P2"), node(3, "P3"), node(4, "P4"));
            if (sum(out).compareTo(pool) > 0) {
                violations.add(pool + "→" + sum(out));
            }
        }
        assertThat(violations).as("拆分合计超过池的用例").isEmpty();
    }

    @Test
    @DisplayName("不变量 sum(amount) ≤ pool:同时链上有留存档位时也成立")
    void sumNeverExceedsPoolWithRetainedNodes() {
        BigDecimal pool = new BigDecimal("1.25");
        List<Split> out = split("1.25", LADDER4,
                node(1, "P1"), node(2, "P2", EXITED), node(3, "P3"), node(4, "P4"));

        assertThat(sum(out)).isLessThanOrEqualTo(pool);
    }

    // ---------- 快照与调用方错误 ----------

    @Test
    @DisplayName("每条流水的 sharePct / positionCode 快照与传入阶梯、节点一致")
    void splitSnapshotsMatchLadderAndNode() {
        List<Split> out = split("100", LADDER4, node(1, "P1"), node(2, "P3"));

        assertThat(out.get(0).positionCode()).isEqualTo("P1");
        assertThat(out.get(0).sharePct()).isEqualTo(50);
        assertThat(out.get(1).positionCode()).isEqualTo("P3");
        assertThat(out.get(1).sharePct()).isEqualTo(85);
        assertThat(out.get(1).diffPct()).isEqualTo(35);
    }

    @Test
    @DisplayName("阶梯里没有链上某个岗位 code:抛 IllegalArgumentException 而不是静默跳过")
    void missingPositionCodeRejected() {
        assertThatThrownBy(() -> split("1000", LADDER2, node(1, "P1"), node(2, "P3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P3");
    }

    @Test
    @DisplayName("空链、null 链、null 请求都抛 IllegalArgumentException")
    void emptyChainRejected() {
        assertThatThrownBy(() -> CommissionEngine.split(new SplitRequest(bd("1000"), List.of(), LADDER4)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommissionEngine.split(new SplitRequest(bd("1000"), null, LADDER4)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommissionEngine.split(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("阶梯为空或 null 抛 IllegalArgumentException")
    void emptyLadderRejected() {
        assertThatThrownBy(() -> CommissionEngine.split(new SplitRequest(bd("1000"), List.of(node(1, "P1")), Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommissionEngine.split(new SplitRequest(bd("1000"), List.of(node(1, "P1")), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同一伙伴在链上出现两次:拒绝,而不是给他发两笔")
    void duplicatePromoterRejected() {
        assertThatThrownBy(() -> split("1000", LADDER4, node(1, "P1"), node(2, "P2"), node(1, "P4")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("待审核(3)的节点:现行实现按正常发放(§2.9 未规定,此用例只是钉住当前行为,变更时需同步改规则)")
    void pendingReviewNodeCurrentlyPaid() {
        List<Split> out = split("1000", LADDER2, node(1, "P1", PENDING), node(2, "P2"));

        assertThat(out).extracting(Split::promoterId).containsExactly(1L, 2L);
    }

    // ---------- helpers ----------

    private static List<Split> split(String pool, Map<String, Integer> ladder, ChainNode... chain) {
        return CommissionEngine.split(new SplitRequest(bd(pool), List.of(chain), ladder));
    }

    private static ChainNode node(long id, String position) {
        return node(id, position, NORMAL);
    }

    private static ChainNode node(long id, String position, int status) {
        return node(id, position, status, false);
    }

    private static ChainNode node(long id, String position, int status, boolean internal) {
        return new ChainNode(id, position, status, internal);
    }

    private static Map<String, Integer> ladder(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static BigDecimal sum(List<Split> out) {
        return out.stream().map(Split::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<String> amounts(List<Split> out) {
        return out.stream().map(s -> s.amount().toPlainString()).toList();
    }
}
