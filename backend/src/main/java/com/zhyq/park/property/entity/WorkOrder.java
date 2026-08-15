package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单(pm_work_order)
 * 工单状态:1待派单 2待接单 3处理中 4待验收 5已完成 6已关闭 7已超时
 * 紧急度:1低 2中 3高
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_work_order")
public class WorkOrder extends BaseEntity {

    /** 工单号(全局唯一) */
    private String code;
    /** 报修/巡检/告警 */
    private String orderType;
    private String title;
    private Long projectId;
    private Long buildingId;
    private Long roomId;
    private Long spaceId;
    /** 位置 */
    private String location;
    /** 分类/工种 */
    private String category;
    /** 紧急度:1低 2中 3高 */
    private Integer urgency;
    /** 工单状态 */
    private Integer status;
    /** 来源(展示用文本,如 '巡检计划'/'安防巡更') */
    private String source;
    /** 来源类型,见 WorkOrderSource 常量;与 sourceId 配对用于反查源记录 */
    private String sourceType;
    /** 来源记录主键 */
    private Long sourceId;
    private String contact;
    private String contactPhone;
    private String images;
    /** 处理人 */
    private String assignee;
    /** 责任单位 id,引用 pm_responsible_unit */
    private Long responsibleUnitId;
    /** 响应SLA(分钟) */
    private Integer slaRespondMin;
    /** 解决SLA(分钟) */
    private Integer slaResolveMin;
    /** 到场时间 */
    private LocalDateTime arriveTime;
    /** 完成时间 */
    private LocalDateTime finishTime;
    /** 满意度评分1-5 */
    private Integer score;
    /** 来源告警id(防重) */
    private Long sourceAlarmId;
    private String remark;
    /** 标准化解决代码 */
    private String resolutionCode;
    /** SLA状态:NULL正常 1响应超时 2解决超时 */
    private Integer slaState;
    /** 是否已升级通知 */
    private Integer escalated;
    /** 回访时间 */
    private LocalDateTime revisitTime;
    /** 回访备注 */
    private String revisitRemark;
}
