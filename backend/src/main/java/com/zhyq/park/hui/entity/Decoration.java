package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 装修及动火申请(srv_decoration)
 * applyType: DECORATION(装修报批申请表) / HOT_WORK(动火作业审批表)
 * 状态:1待审批 2施工中 3已完工 4已驳回
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_decoration")
public class Decoration extends BaseEntity {
    private String applyType;
    private String applyNo;
    private String applicantUnit;
    private LocalDate applyDate;
    private String workArea;
    private Long tenantRefId;
    private Long roomId;
    private String contractor;
    private String contact;
    private String phone;
    private LocalDate startDate;
    private LocalDate endDate;
    private String scope;
    private String formDataJson;
    private Long sealId;
    private String sealConfigJson;
    private Integer status;
}
