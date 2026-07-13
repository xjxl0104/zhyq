package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单流转(pm_work_order_log)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_work_order_log")
public class WorkOrderLog extends BaseEntity {

    private Long orderId;
    /** 派单/接单/到场/处理/验收/评价/关闭 */
    private String action;
    private String operator;
    private String content;
}
