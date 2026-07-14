package com.zhyq.park.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作流-节点任务(wf_task),每到一个节点生成一条。
 * assignee 为占位审批人(约定值),真实指派待 #7 鉴权轮。
 * status:1待审 2通过 3驳回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task")
public class WfTask extends BaseEntity {

    private Long instanceId;
    private Long nodeId;
    private Integer seq;
    private String assignee;
    private Integer status;
    private String opinion;
    private LocalDateTime actTime;
}
