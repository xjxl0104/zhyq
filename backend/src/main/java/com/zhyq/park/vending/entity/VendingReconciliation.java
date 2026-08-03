package com.zhyq.park.vending.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_vending_reconciliation")
public class VendingReconciliation extends BaseEntity {
    private String vendorSettlementId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal salesAmount;
    private BigDecimal refundAmount;
    private BigDecimal platformFee;
    private BigDecimal netAmount;
    private String settlementStatus;
    private Long sourceBatchId;
}
