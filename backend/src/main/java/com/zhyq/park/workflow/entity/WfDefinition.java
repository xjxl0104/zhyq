package com.zhyq.park.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流-流程定义(wf_definition)。
 * 一个 bizType 可配多条,但同一时刻只应有一条 status=1 启用。
 * status:1启用 0停用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_definition")
public class WfDefinition extends BaseEntity {

    private String bizType;
    private String name;
    private Integer status;
}
