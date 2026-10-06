package com.zhyq.park.pur.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_tenant_contact")
public class TenantContact extends BaseEntity {
    @TableField("tenant_name")
    private String name;
    private Long projectId;
    private Long tenantRefId;
    private String contact;
    private String phone;
}
