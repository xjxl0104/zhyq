package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收据(fin_receipt)。收款成功后自动生成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_receipt")
public class Receipt extends BaseEntity {

    /** 收据号 */
    private String receiptNo;
    /** 关联收款单 */
    private Long paymentId;
    private Long billId;
    private Long tenantRefId;
    private BigDecimal amount;
    /** 收款人 */
    private String payee;
    /** 0=正常 1=已作废(对应收款被撤销红冲) */
    private Integer voidStatus;
    /** 打印次数 */
    private Integer printCount;
    /** 最后打印时间 */
    private LocalDateTime lastPrintTime;
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
