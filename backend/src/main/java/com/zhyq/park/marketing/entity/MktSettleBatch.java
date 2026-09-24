package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 佣金结算批次(表 crm_settle_batch,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_settle_batch")
public class MktSettleBatch extends BaseEntity {
    /** SB-yyyyMMdd-0001 */
    private String batchNo;
    private Integer cnt;
    private BigDecimal totalAmount;
    private String operator;
    private String remark;
    private Long projectId;
}
