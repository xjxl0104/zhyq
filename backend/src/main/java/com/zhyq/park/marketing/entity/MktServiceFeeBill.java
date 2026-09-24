package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_service_fee_bill")
public class MktServiceFeeBill extends BaseEntity {
    private Long contractId;
    private Long warehouseId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String billingKey;
    private Integer status;
    private BigDecimal amount;
    private String disputeReason;
    private String receiptNo;
    private String receiptProof;
    private java.time.LocalDateTime receivedAt;
    private String receivedBy;
    private Long projectId;
}
