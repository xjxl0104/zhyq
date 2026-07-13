package com.zhyq.park.property.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会议室预约(pm_meeting_booking)
 * 状态:1待核销 2已核销 3已取消 4已退款
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_meeting_booking")
public class MeetingBooking extends BaseEntity {

    private Long roomId;
    private Long tenantRefId;
    private String booker;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private BigDecimal fee;
}
