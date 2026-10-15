package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_yunshan_sync_log")
public class MktYunshanSyncLog extends BaseEntity {
    private Long mapId;
    /** 1 定时；2 手动补拉。 */
    private Integer triggerType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer ok;
    private Integer daysFetched;
    private Integer daysChanged;
    private Integer daysMissing;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long totalConsign;
    private LocalDate latestStatDate;
    private LocalDateTime dataAsOf;
    private String errorCode;
    private String errorMsg;
}
