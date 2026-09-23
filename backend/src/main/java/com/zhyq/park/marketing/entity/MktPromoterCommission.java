package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 佣金流水(表 crm_promoter_commission,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_promoter_commission")
public class MktPromoterCommission extends BaseEntity {
    private Long referralOrderId;
    /** 收款人 */
    private Long promoterId;
    /** 收款人岗位快照 */
    private String positionCode;
    /** 岗位份额快照 */
    private Integer sharePct;
    /** 级差 = share − 下级已拿 */
    private Integer diffPct;
    private Long ruleId;
    private BigDecimal baseAmount;
    /** 实际比例% = 总比例 × diff/100(租赁为 月数×diff/100) */
    private BigDecimal rate;
    /** 正向为佣金,负向为扣回 */
    private BigDecimal amount;
    /** 1正向 -1扣回 */
    private Integer sign;
    /** 0 = original commission/permanent clawback; >0 = paired receipt reversal/restoration cycle. */
    private Integer adjustmentSequence;
    /** On the original positive row only: last issued receipt adjustment cycle. */
    private Integer receiptRevision;
    /** On the original positive row only: 0 eligible, 1 temporarily suspended, 2 permanently revoked. */
    private Integer receiptSuspended;
    /** 1冻结 2可结算 3已结算 4已提现 5作废 */
    private Integer status;
    /** 路径 B 自动解冻时间;路径 A 为空,靠到账事件 */
    private LocalDateTime unfreezeAt;
    private Long settleBatchId;
    private Long withdrawalId;
    private String voidReason;
    private Long projectId;
}
