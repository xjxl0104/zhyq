package com.zhyq.park.marketing.engine;

import java.math.BigDecimal;

/**
 * 拆给一个收款人的结果(对应一行 crm_promoter_commission)。
 *
 * @param sharePct 收款人岗位份额快照
 * @param diffPct  级差 = share − 下级已拿
 * @param amount   = pool × diffPct / 100,HALF_UP 2 位
 */
public record Split(Long promoterId, String positionCode, int sharePct, int diffPct, BigDecimal amount) {
}
