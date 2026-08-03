package com.zhyq.park.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_deposit_ledger")
public class DepositLedger extends BaseEntity {
    private Long registerId;
    private String depositType;
    private BigDecimal requiredAmount;
    private BigDecimal confirmedReceivedAmount;
    private BigDecimal differenceAmount;
    private BigDecimal sourceDifferenceAmount;
    private String status;
}
