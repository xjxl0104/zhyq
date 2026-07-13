package com.zhyq.park.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_alarm")
public class Alarm extends BaseEntity {
    private Long deviceId;
    private String alarmType;
    private Integer level;
    private Integer status;
    private String location;
    private String content;
    private LocalDateTime alarmTime;
}
