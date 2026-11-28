package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 巡检任务检查项现场拍照打卡记录(pm_routine_task_item) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_routine_task_item")
public class PmRoutineTaskItem extends BaseEntity {
    private Long taskId;
    private Long templateItemId;
    private Integer seqNo;
    private String itemName;
    private String areaPoint;
    private String standard;
    private Integer requirePhoto;
    private Integer minPhotos;
    private Integer checked;
    /** NORMAL | ABNORMAL */
    private String result;
    private String issueCategory;
    private String issueDesc;
    private String photoFileIds;
    private String photoUrlsJson;
    private String capturedBy;
    private LocalDateTime capturedTime;
    private Long workOrderId;
    private String workOrderCode;
}
