package com.zhyq.park.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流-节点定义(wf_node),属于某 definition,按 seq 顺序流转。
 * approverType:role/user/dept(本期先支持 role/user);approverValue 为占位约定值,真实指派待 #7 鉴权轮。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_node")
public class WfNode extends BaseEntity {

    private Long definitionId;
    private Integer seq;
    private String name;
    private String approverType;
    private String approverValue;
}
