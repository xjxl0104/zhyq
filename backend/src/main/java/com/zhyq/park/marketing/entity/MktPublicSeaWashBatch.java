package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公海入池三道防撞洗数批次审计记录（供三层下钻同源聚合）。
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
    private String blockedDetailsJson;
    private String operator;
    private LocalDateTime createdAt;
}
