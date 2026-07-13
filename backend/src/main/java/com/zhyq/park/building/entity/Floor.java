package com.zhyq.park.building.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_floor")
public class Floor extends BaseEntity {
    private Long buildingId;
    private Long projectId;
    private String name;
    private Integer floorNo;
    private BigDecimal buildArea;
    private Integer sort;
}
