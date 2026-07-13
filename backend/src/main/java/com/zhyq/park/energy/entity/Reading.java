package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_reading")
public class Reading extends BaseEntity {
    private Long meterId;
    private BigDecimal prevReading;
    private BigDecimal currReading;
    private BigDecimal usageAmount;
    private String readSource;
    private LocalDateTime readTime;
    private BigDecimal fee;
}
