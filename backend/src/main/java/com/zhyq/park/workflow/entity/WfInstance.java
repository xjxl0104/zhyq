package com.zhyq.park.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流-审批实例(wf_instance),一次审批一条。
 * approvalId 关联 biz_approval 单据头(D1-方案A)。
 * status:1审批中 2通过 3驳回 4撤回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_instance")
public class WfInstance extends BaseEntity {

    private Long definitionId;
    private String bizType;
    private Long bizId;
    private Long approvalId;
    private Integer currentSeq;
    private Integer status;
}
