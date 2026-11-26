package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_notice_header_profile")
public class NoticeHeaderProfile extends BaseEntity {

    /** LEASE(租金/物业费) | UTILITY(水电/垃圾费) | DEPOSIT(保证金) */
    private String scopeType;
    private String tenantName;
    private String parkName;
    private String noticePrefix;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate issueDate;
    private Integer issueMonthOffset;
    private Integer issueDay;
    private LocalDate dueDate;
    private Integer dueMonthOffset;
    private Integer dueDay;
    private String contactName;
    private String contactPhone;
    private String paymentAccount;
    private String rentPayeeName;
    private String rentContactName;
    private String rentContactPhone;
    private String rentPaymentAccount;
    private String propertyPayeeName;
    private String propertyContactName;
    private String propertyContactPhone;
    private String propertyPaymentAccount;
}
