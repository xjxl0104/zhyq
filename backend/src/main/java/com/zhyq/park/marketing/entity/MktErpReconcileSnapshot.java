package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_erp_reconcile_snapshot")
public class MktErpReconcileSnapshot extends BaseEntity {
    private Long warehouseId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private BigDecimal diffAmount;
    private BigDecimal diffRatio;
    private Integer frozen;
    private String reason;
    private Long projectId;
}
