package com.zhyq.park.marketing.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 佣金级差引擎(PARK-MKT-001 §2.7):把佣金池按岗位份额沿收款链向上拆分。纯函数,不碰数据库。
 *
 * <p>从成交人开始,{@code taken = 0};每个节点 {@code share = ladder[position]}:
 * <ul>
 *   <li>{@code share <= taken}:同级或倒挂,压缩跳过;</li>
 *   <li>否则 {@code diff = share - taken},{@code amount = pool × diff / 100}(HALF_UP 2 位),{@code taken = share};</li>
 *   <li>节点已退出(status=4)或内部人员(internal):这一档园区留存 —— {@code taken} 照样推进但不出流水
 *       (§2.9 / §2.13,避免"踢人得利");</li>
 *   <li>冻结(status=2)照常出流水,只是不能提现(§2.9);</li>
 *   <li>{@code share == 100} 或链走完即结束;未到 100 的剩余部分园区留存。</li>
 * </ul>
 * 保证:sum(amount) ≤ pool;无重复收款人;ladder 缺岗位、空链视为调用方错误。
 */
public final class CommissionEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int STATUS_EXITED = 4;

    private CommissionEngine() {
    }

    /** 把佣金池按级差拆给收款人。chain 从成交人开始向上到根。 */
    public static List<Split> split(SplitRequest req) {
        if (req == null || req.chain() == null || req.chain().isEmpty()) {
            throw new IllegalArgumentException("收款链不能为空");
        }
        if (req.ladder() == null || req.ladder().isEmpty()) {
            throw new IllegalArgumentException("岗位阶梯不能为空");
        }
        BigDecimal pool = req.poolAmount() == null ? BigDecimal.ZERO : req.poolAmount();
        List<Split> out = new ArrayList<>();
        if (pool.signum() <= 0) {
            return out;
        }

        Map<String, Integer> ladder = req.ladder();
        Set<Long> seen = new HashSet<>();
        int taken = 0;
        BigDecimal allocated = BigDecimal.ZERO;
        for (ChainNode node : req.chain()) {
            if (!seen.add(node.promoterId())) {
                throw new IllegalArgumentException("收款链出现重复伙伴: " + node.promoterId());
            }
            Integer share = ladder.get(node.positionCode());
            if (share == null) {
                throw new IllegalArgumentException("岗位阶梯缺少岗位: " + node.positionCode());
            }
            if (share <= taken) {
                continue;
            }
            int diff = share - taken;
            taken = share;
            boolean retainedByPark = node.status() == STATUS_EXITED || node.internal();
            if (!retainedByPark) {
                BigDecimal amount = pool.multiply(BigDecimal.valueOf(diff))
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                BigDecimal remaining = pool.subtract(allocated);
                if (amount.compareTo(remaining) > 0) {
                    amount = remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                }
                allocated = allocated.add(amount);
                out.add(new Split(node.promoterId(), node.positionCode(), share, diff, amount));
            }
            if (taken >= 100) {
                break;
            }
        }
        return out;
    }
}
