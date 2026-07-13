package com.zhyq.park.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_tenant_staff")
public class TenantStaff extends BaseEntity {
    private Long tenantRefId;
    private String name;
    private String dept;
    private String post;
    private String phone;
    private String accessPerm;
    private String plateNo;
    private LocalDate validEnd;
    private Integer status;
}
