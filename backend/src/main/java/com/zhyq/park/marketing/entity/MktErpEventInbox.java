package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_erp_event_inbox")
public class MktErpEventInbox extends BaseEntity {
    private Long warehouseId;
    private String appId;
    private String eventId;
    private String event;
    private String orderNo;
    private String payloadDigest;
    private String payloadJson;
    private LocalDateTime occurredAt;
    private String status;
    private Integer attempts;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime processedAt;
    private Long projectId;
}
