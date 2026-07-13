package com.zhyq.park.finance.entity;

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
    /** 打印次数 */
    private Integer printCount;
    /** 最后打印时间 */
    private LocalDateTime lastPrintTime;
    private String remark;
}
