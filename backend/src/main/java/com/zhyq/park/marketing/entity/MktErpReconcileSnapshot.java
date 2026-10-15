package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    /** New workflow uses outbound-order counts, never the legacy amount columns. */
    private String metric;
    private String state;
    private Integer revision;
    private Long previousSnapshotId;
    private Long externalOrderCount;
    private Long systemOrderCount;
    private Long diffOrderCount;
    private Integer blockingCount;
    private String sourceName;
    private String evidence;
    private String dataFingerprint;
    private String detailsJson;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
}
