package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 每日/每班次巡检任务实例(pm_routine_task) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_routine_task")
public class PmRoutineTask extends BaseEntity {
    public static final String ST_PENDING = "PENDING";
    public static final String ST_IN_PROGRESS = "IN_PROGRESS";
    public static final String ST_COMPLETED = "COMPLETED";
    public static final String ST_OVERDUE = "OVERDUE";

    private String moduleType;
    private Long templateId;
    private String templateName;
    private String area;
    private LocalDate taskDate;
    private String slotLabel;
    private LocalDateTime scheduledStart;
    private LocalDateTime deadlineTime;
    private Long inspectorId;
    private String inspectorName;
    private String inspectorPhone;
    private String inspectorPosition;
    private Integer totalItems;
    private Integer completedItems;
    private Integer issueCount;
    private String status;
    private LocalDateTime completedTime;
}
