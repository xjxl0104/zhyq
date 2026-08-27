package com.zhyq.park.property.model;

import java.util.Set;

/**
 * 工单来源类型常量。
 * 原先只有一个无约束的 source 文本列, 转单时不写源记录主键,
 * 导致工单无法反查源巡检/巡更记录。sourceType + sourceId 配对使用。
 */
public final class WorkOrderSource {

    /** 巡检计划转单, sourceId = pm_inspection_plan.id */
    public static final String INSPECTION_PLAN = "INSPECTION_PLAN";

    /** 安防巡更异常转单, sourceId = pm_patrol.id */
    public static final String PATROL = "PATROL";

    /** 三检记录转单, sourceId = pm_check.id */
    public static final String CHECK = "CHECK";

    /** 投诉/意见转单, sourceId = pm_feedback.id */
    public static final String FEEDBACK = "FEEDBACK";

    /** 规则中心告警转单, sourceId = 告警 id(亦保留在 sourceAlarmId) */
    public static final String ALARM = "ALARM";

    /** 手工新建 */
    public static final String MANUAL = "MANUAL";

    /**
     * 可反查的来源类型白名单。MANUAL 不入内:手工工单没有源记录,
     * 反查无意义,放进来只会多一个点不开的入口。
     */
    private static final Set<String> QUERYABLE = Set.of(
            INSPECTION_PLAN, PATROL, CHECK, FEEDBACK, ALARM);

    /**
     * 校验来源类型是否支持反查。sourceType 来自外部入参,不校验的话
     * 非法值会静默返回空列表而不是明确报错, 前端难排查。
     */
    public static boolean isQueryable(String sourceType) {
        return sourceType != null && QUERYABLE.contains(sourceType);
    }

    private WorkOrderSource() {}
}
