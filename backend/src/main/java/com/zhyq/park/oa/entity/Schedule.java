package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_schedule")
public class Schedule extends BaseEntity {
    private String title;
    private String stype;
    private String owner;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer remind;
    private String remark;
}
