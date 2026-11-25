package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 内部专属公海池客户画像、广东战区分区、企查工商对齐、高德地推坐标与 72h/15d 保活状态。
 */
@Data
@TableName("crm_public_sea_meta")
public class MktPublicSeaMeta {
    @TableId(value = "customer_id", type = IdType.INPUT)
    private Long customerId;
    private Long tenantId;
    private Long projectId;
    /** CALC_HOT / FEIYU_AD / AMAP_POI / CHANMAMA / TUNGEE_JOB / EXPIRE_RETURN / PARK_CRM / MANUAL_IMPORT */
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

    /** V132 广东分区、工商对齐与高德地推扩展 */
    private String province;
    private String city;
    private String district;
    /** GZ / DG / SZ / FS / YD / OTHER_GD / OUT_GD */
    private String zoneCode;
    private String address;
    private String buildingName;
    private BigDecimal lng;
    private BigDecimal lat;
    private Long nearestWhId;
    private String nearestWhName;
    private BigDecimal distNearestWhKm;
    private String creditCode;
    private String legalPerson;
    private String regCapital;
    private Integer insuredCount;
    private Integer dishonestCount;
    /** 0未配Key或待补 1已对齐 2外省锁跳过省钱 */
    private Integer qccAligned;
    private LocalDateTime visitProtectedUntil;
    private Integer lastCheckinDistM;
    private LocalDateTime lastCheckinAt;
    private String lastCheckinPhoto;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
