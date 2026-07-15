package com.zhyq.park.acc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 访客预约/到访(acc_visitor,#21)。
 * status: 1已预约 2已到访 3已离场 4已取消。状态机见 {@link com.zhyq.park.acc.service.AccVisitorService}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_visitor")
public class AccVisitor extends BaseEntity {

    private String name;
    private String phone;
    private String hostUser;
    private Long spaceId;
    private LocalDateTime visitTime;
    private Integer status;
    private String plateNo;
    private String remark;
}
