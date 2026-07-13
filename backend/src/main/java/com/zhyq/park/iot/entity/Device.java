package com.zhyq.park.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_device")
public class Device extends BaseEntity {
    private String code;
    private String name;
    private String category;
    private String vendor;
    private Long projectId;
    private Long buildingId;
    private String location;
    private Integer status;
}
