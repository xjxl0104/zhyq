package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 物业巡检固定模板(pm_routine_template) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_routine_template")
public class PmRoutineTemplate extends BaseEntity {
    /** EQUIPMENT | PATROL | CLEANING | GREENING */
    private String moduleType;
    private String name;
    private String area;
    private Long inspectorId;
    private String inspectorName;
    private String inspectorPhone;
    private String inspectorPosition;
    /** DAILY_SLOTS 每日固定时段 | INTERVAL_HOURS 24小时内每隔N小时 */
    private String freqType;
    private Integer intervalHours;
    private String startTime;
    private String endTime;
    private String timeSlots;
    private String weekdays;
    private Integer status;
    private Integer sortOrder;
    private String remark;
}
