package com.zhyq.park.budget.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算(bud_budget)。budgetType:1年度预算 2月度预算。
 * 状态:1草稿 2审批中 3已通过 4已驳回 5已归档 6已取消 — 审批中/通过/驳回由审批链回调驱动。
 * amount 仅登记预算金额,绝不入账/触发收款(不写 finance),与采购申请同一边界。
 * 预算计划文件走统一附件表 sys_file(bizType='budget'),不在本表存路径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bud_budget")
public class Budget extends BaseEntity {

    private String budgetNo;
    private String title;
    private Integer budgetType;
    private String period;
    private String department;
    private String applicant;
    private BigDecimal amount;
    private Integer status;
    private String approver;
    private LocalDateTime approveTime;
    /** 审批意见不在此冗余:逐节点意见存于 wf_task.opinion,详情页「审批轨迹」按节点展示 */
    private String remark;
}
