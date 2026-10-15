package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Terminal order decisions survive missing customer mappings and later manual imports. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_erp_order_state")
public class MktErpOrderState extends BaseEntity {
    private Long warehouseId;
    private String orderNo;
    private String terminalEvent;
    private String appId;
    private String eventId;
    private LocalDateTime occurredAt;
    private Long projectId;
}
