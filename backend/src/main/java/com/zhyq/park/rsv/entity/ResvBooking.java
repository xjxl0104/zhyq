package com.zhyq.park.rsv.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一预订记录(rsv_booking,#23)。
 * status: 1已预订 2已取消 3已完成。
 * fee: 按 {@link com.zhyq.park.rsv.service.BookingPolicy} 算出并仅存于本记录,绝不写账单/收款。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rsv_booking")
public class ResvBooking extends BaseEntity {

    private Long resourceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String booker;
    private String purpose;
    private Integer status;
    private BigDecimal fee;
}
