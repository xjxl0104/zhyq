package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 物业活动(pm_activity)
 * 状态:1报名中 2进行中 3已结束
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_activity")
public class Activity extends BaseEntity {

    private String title;
    private String content;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 报名人数 */
    private Integer enrollCount;
    /** 1报名中 2进行中 3已结束 */
    private Integer status;
}
