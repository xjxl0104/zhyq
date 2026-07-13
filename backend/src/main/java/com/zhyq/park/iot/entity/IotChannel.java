package com.zhyq.park.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_channel")
public class IotChannel extends BaseEntity {
    private Long deviceId;
    private Integer channelNo;
    private String name;
    private Integer status;
}
