package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_erp_unmapped")
public class MktErpUnmapped extends BaseEntity {
    private Long warehouseId;
    private String customerCode;
    private String orderNo;
    private String eventId;
    private Long inboxId;
    private String status;
    private String payloadJson;
    private Long mappedCustomerId;
    private java.time.LocalDateTime replayedAt;
    private Long projectId;
}
