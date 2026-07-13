package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_meter")
public class Meter extends BaseEntity {
    private String code;
    private String name;
    private String energyType;
    private Long projectId;
    private Long buildingId;
    private Long roomId;
    private BigDecimal ratio;
    private BigDecimal lastReading;
    private Integer status;
}
