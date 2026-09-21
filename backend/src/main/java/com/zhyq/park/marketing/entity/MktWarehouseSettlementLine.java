package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data @EqualsAndHashCode(callSuper = true) @TableName("crm_warehouse_settlement_line")
public class MktWarehouseSettlementLine extends BaseEntity {
    private Long settlementId; private Long billId; private Long referralOrderId; private BigDecimal amount; private String snapshotJson;
}
