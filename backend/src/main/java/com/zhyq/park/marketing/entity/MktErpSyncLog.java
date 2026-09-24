package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** ERP 同步日志(表 crm_erp_sync_log,V59) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_erp_sync_log")
public class MktErpSyncLog extends BaseEntity {
    private Long warehouseId;
    private String appId;
    private String direction;
    private String event;
    private String orderNo;
    private Integer httpStatus;
    private Integer ok;
    /** SIGN_INVALID / APP_DISABLED / BAD_PAYLOAD / WH_MISMATCH / NONCE_REPLAY / UNMAPPED … */
    private String errorCode;
    private String error;
    /** body 的 sha256,排错不落原文 */
    private String payloadDigest;
    private Long referralOrderId;
    private Long projectId;
}
