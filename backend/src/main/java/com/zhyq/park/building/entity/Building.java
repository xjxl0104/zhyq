package com.zhyq.park.building.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_building")
public class Building extends BaseEntity {
    private Long projectId;
    private String code;
    private String name;
    private Integer floorCount;
    private BigDecimal buildArea;
    private String usageType;
    private Integer status;
    private Integer sort;
}
