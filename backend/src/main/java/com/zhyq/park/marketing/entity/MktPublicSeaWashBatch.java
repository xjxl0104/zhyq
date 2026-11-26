package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公海入池三道防撞洗数与广东省钱对齐批次审计记录。
 */
@Data
@TableName("crm_public_sea_wash_batch")
public class MktPublicSeaWashBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private String batchNo;
    private String seaSource;
    private String fileName;
    private Integer totalRows;
    private Integer insertedRows;
    private Integer blockedPrivateLock;
    private Integer blockedWhHmac;
    private Integer blockedDupAgency;
    private Integer skippedOutProvince;
    private Integer qccCacheHits;
    private Integer qccApiCalls;
    private String blockedDetailsJson;
    private String operator;
    private LocalDateTime createdAt;
    /** RUNNING / DONE / FAILED（V134） */
    private String status;
    private Integer processedRows;
    private String errorMsg;
    private LocalDateTime finishedAt;
}
