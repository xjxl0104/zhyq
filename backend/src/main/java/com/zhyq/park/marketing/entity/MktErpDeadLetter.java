package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_erp_dead_letter")
public class MktErpDeadLetter extends BaseEntity {
    private Long inboxId;
    private Long warehouseId;
    private String eventId;
    private String orderNo;
    private String errorCode;
    private String errorMessage;
    private Integer attempts;
    private java.time.LocalDateTime replayedAt;
    private String replayedBy;
    private Long projectId;
}
