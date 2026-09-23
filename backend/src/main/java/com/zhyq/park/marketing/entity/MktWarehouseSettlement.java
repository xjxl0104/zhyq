package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @EqualsAndHashCode(callSuper = true) @TableName("crm_warehouse_settlement")
public class MktWarehouseSettlement extends BaseEntity {
    private Long warehouseId; private String batchNo; private LocalDate periodStart; private LocalDate periodEnd;
    private String payNo; private String payProof; private java.time.LocalDateTime paidAt; private String paidBy;
    private Integer status; private BigDecimal amount; private String frozenReason; private Long projectId;
}
