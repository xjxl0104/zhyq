package com.zhyq.park.pur.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购申请(pur_request)。
 * 状态:1草稿 2审批中 3已通过 4已驳回 5已完成 6已取消 — 审批中/通过/驳回由审批链回调驱动。
 * totalAmount 仅记录采购预算/实付金额,绝不入账/触发收款(不写 finance)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pur_request")
public class PurRequest extends BaseEntity {

    private String requestNo;
    private Long planId;
    private String title;
    private String supplier;
    private String applicant;
    private String department;
    private Long spaceId;
    private BigDecimal totalAmount;
    private Integer status;
    private String approver;
    private LocalDateTime approveTime;
    /** 审批意见不在此冗余:逐节点意见存于 wf_task.opinion,详情页「审批轨迹」按节点展示 */
    private String remark;

    /** 明细行,创建/详情时携带,不落 pur_request 表。附件由前端走 /file/attach 关联(同合同页做法) */
    @TableField(exist = false)
    private List<PurRequestItem> items;
}
