package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_marketing_credential")
public class MktCredential extends BaseEntity {
    private String identityType;
    private Long identityId;
    private String username;
    @JsonIgnore @ToString.Exclude
    private String passwordHash;
    @JsonIgnore @ToString.Exclude
    private String registrationPhone;
    private LocalDateTime agreedAt;
    private Integer status;
}
