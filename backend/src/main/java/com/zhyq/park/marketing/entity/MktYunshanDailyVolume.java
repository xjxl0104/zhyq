package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 只保存云山实际返回的日统计；没有统计记录不能解释为零票。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_yunshan_daily_volume")
public class MktYunshanDailyVolume extends BaseEntity {
    private Long mapId;
    private Long customerId;
    private Long warehouseId;
    private String parentOwnerNo;
    private String parentWarehouseNo;
    private LocalDate statDate;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long consignNum;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long firstConsignNum;
    private Integer revisedCount;
    private LocalDateTime lastRevisedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime dataAsOf;
    private LocalDateTime fetchedAt;
    private Long projectId;
}
