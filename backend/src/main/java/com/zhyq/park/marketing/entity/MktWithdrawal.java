package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 伙伴提现单(表 crm_withdrawal,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_withdrawal")
public class MktWithdrawal extends BaseEntity {
    /** WD-yyyyMMdd-0001 */
    private String withdrawalNo;
    private Long promoterId;
    /** 税前 */
    private BigDecimal amount;
    /** 1个税代扣 2灵工平台代征 */
    private Integer taxMode;
    private BigDecimal taxAmount;
    /** 税后实付 */
    private BigDecimal netAmount;
    /** 打款幂等键 */
    private String payNo;
    /** 1线下 2微信商家转账 */
    private Integer payMethod;
    /** 1待审核 2已审核 3已打款 4已驳回 */
    private Integer status;
    private String auditBy;
    private LocalDateTime auditAt;
    private String payBy;
    private LocalDateTime payAt;
    private String rejectReason;
    /** 本次提现覆盖的流水 id */
    private String commissionIds;
    private Long projectId;
}
