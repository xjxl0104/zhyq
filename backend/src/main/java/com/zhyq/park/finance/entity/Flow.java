package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收支流水。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_flow")
public class Flow extends BaseEntity {

    private String flowNo;
    /** 1收 2支 */
    private Integer direction;
    private BigDecimal amount;
    private Long billId;
    private Long paymentId;
    /** 0未匹配 1已匹配 */
    private Integer matchStatus;
    private LocalDateTime flowTime;
    private String remark;

    // ---- 展示字段(不落库):下游记录只存 bill_id, 光有 id 用户看不出这笔钱是谁的、对应哪张账单。
    //      口径由 FinanceViewEnricher 统一填充, 与所有账单页一致。
    /** 关联账单号 */
    @TableField(exist = false)
    private String billCode;
    /** 租客名(登记明细优先, 租客档案兜底) */
    @TableField(exist = false)
    private String tenantName;
    /** 关联账单的费用类型 */
    @TableField(exist = false)
    private String feeType;
}
