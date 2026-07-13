package com.zhyq.park.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_tenant")
public class BizTenant extends BaseEntity {
    private String code;
    private String name;
    private Integer tenantType;
    private String inviteCode;
    private String contact;
    private String phone;
    private Long projectId;
    private String industry;
    private String tags;
    private String creditCode;
    private String legalPerson;
    private String regAddress;
    private LocalDate establishDate;
    private LocalDate bizEndDate;
    private Integer status;
    private String remark;
}
