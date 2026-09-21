package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 云仓 ERP 接入凭证(表 crm_warehouse_erp,V59) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_warehouse_erp")
public class MktWarehouseErp extends BaseEntity {
    private Long warehouseId;
    /** 1 webhook 推送(唯一已实现) 2 我方拉取 3 文件 */
    private Integer mode;
    /** wh_xxxx,请求头 X-App-Id */
    private String appId;
    /** HMAC 密钥 AES-GCM 密文,只在签发时明文回显一次 */
    private String secretEnc;
    /** 1 沙箱 2 正式 */
    private Integer env;
    /** 1 启用 0 停用 */
    private Integer status;
    /** 预留:园区回推给云仓 */
    private String callbackUrl;
    /** 预留:拉取模式字段映射 */
    private String fieldMapping;
    /** 最近一次收到事件 */
    private LocalDateTime lastSyncAt;
    private Integer failCount;
    private Long projectId;
}
