package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 装修申请(srv_decoration)
 * 状态:1待审批 2施工中 3已完工 4已驳回
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_decoration")
public class Decoration extends BaseEntity {
    private Long tenantRefId;
    private Long roomId;
    private String contractor;
    private String contact;
    private String phone;
    private LocalDate startDate;
    private LocalDate endDate;
    private String scope;
    private Integer status;
}
