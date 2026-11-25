package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_periodic_task")
public class PeriodicTask extends BaseEntity {
    private String name;
    private String category;
    private String cycleType;
    private String monthsJson;
    private Integer nodeDay;
    private String owner;
    private String vendor;
    private String color;
    private String description;
    private Integer status;
    private Integer sort;
}
