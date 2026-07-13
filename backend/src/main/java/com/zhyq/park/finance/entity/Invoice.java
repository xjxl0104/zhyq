package com.zhyq.park.finance.entity;

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
}
