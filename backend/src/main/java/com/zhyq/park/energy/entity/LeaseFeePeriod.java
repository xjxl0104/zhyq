package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_lease_fee_period")
public class LeaseFeePeriod extends BaseEntity {

    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_CONFIRMED = "CONFIRMED";

    private Long projectId;
    private String period;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private String parkName;
    private String noticePrefix;

    private String rentPayeeName;
    private String rentPaymentAccount;
    private String rentContactName;
    private String rentContactPhone;

    private String propertyPayeeName;
    private String propertyPaymentAccount;
    private String propertyContactName;
    private String propertyContactPhone;

    private LocalDate issueDate;
    private LocalDate dueDate;
    private String remark;

    private BigDecimal rentTotalAmount;
    private BigDecimal propertyTotalAmount;
    private Integer noticeCount;
    private LocalDateTime generatedTime;
    private LocalDateTime inputsChangedTime;
    private String snapshotJson;

    @TableField(exist = false)
    private Boolean noticeStale;

    @TableField(exist = false)
    private String activeKey;
}
