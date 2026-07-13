package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 场地预约(srv_site_booking)
 * 状态:1待审批 2已通过 3已取消
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_site_booking")
public class SiteBooking extends BaseEntity {
    private String siteName;
    private String booker;
    private String phone;
    private Long tenantRefId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String purpose;
    private BigDecimal fee;
    private Integer status;
}
