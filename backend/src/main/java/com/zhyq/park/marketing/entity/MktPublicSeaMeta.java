package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 内部专属公海池客户画像与 72h 认领保活状态（1:1 关联 crm_customer.id，不对原表做 ALTER 锁表）。
 */
@Data
@TableName("crm_public_sea_meta")
public class MktPublicSeaMeta {
    @TableId(value = "customer_id", type = IdType.INPUT)
    private Long customerId;
    private Long tenantId;
    private Long projectId;
    /** CALC_HOT / CHANMAMA / TUNGEE_JOB / EXPIRE_RETURN / PARK_CRM / MANUAL_IMPORT */
    private String seaSource;
    private String shopName;
    private String category;
    private Integer estDailyOrders;
    private BigDecimal avgWeightKg;
    private BigDecimal calcLowPrice;
    private BigDecimal calcHighPrice;
    private BigDecimal logisticsScore;
    /** S / A / B */
    private String urgencyLevel;
    private String urgencyReason;
    private String blockNote;
    /** UNCLAIMED / CLAIMED_ADMIN / FEED_WH / CLAIMED_WH / ARCHIVED_COLD */
    private String claimStatus;
    /** ADMIN / WAREHOUSE */
    private String claimHolderType;
    private Long claimHolderId;
    private String claimHolderName;
    private LocalDateTime claimedAt;
    private LocalDateTime slaDeadlineAt;
    private LocalDateTime firstTouchedAt;
    private String firstTouchNote;
    private String feedWarehouseIds;
    private Integer releaseCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
