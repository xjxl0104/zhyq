package com.zhyq.park.marketing.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 级差拆分的输入。
 *
 * @param poolAmount 佣金池(园区最多为这笔成交付出的佣金总额),已由 {@link PoolCalculator} 算好
 * @param chain      收款链:index 0 = 成交伙伴,之后依次是其上级、上上级……直到根;不含岗位阶梯之外的层
 * @param ladder     岗位阶梯 positionCode → share_pct(0–100,严格递增,顶格 100);钻石合伙人 override 已合并进来
 */
public record SplitRequest(BigDecimal poolAmount, List<ChainNode> chain, Map<String, Integer> ladder) {
}
