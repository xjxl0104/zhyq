package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 发票。状态:1申请 2审核 3已开 4红冲 5作废。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_invoice")
public class Invoice extends BaseEntity {

    private String code;
    private Long billId;
    private Long tenantRefId;
    /** 发票抬头 */
    private String title;
    /** 税号 */
    private String taxNo;
    private BigDecimal amount;
    /** 普票/专票 */
    private String invoiceType;
    /** 1申请 2审核 3已开 4红冲 5作废 */
    private Integer status;
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
