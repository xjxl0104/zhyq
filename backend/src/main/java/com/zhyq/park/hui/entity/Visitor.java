package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_visitor")
public class Visitor extends BaseEntity {
    private String visitorName;
    private String phone;
    private String host;
    private Long tenantRefId;
    private LocalDateTime visitTime;
    private String reason;
    private String plateNo;
    private String qrCode;
    private Integer status;
}
