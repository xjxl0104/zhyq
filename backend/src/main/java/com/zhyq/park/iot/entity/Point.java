package com.zhyq.park.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_point")
public class Point extends BaseEntity {
    private String name;
    private Long projectId;
    private Long buildingId;
    private String floorName;
    private String location;
    private Integer status;
    private String remark;
}
