package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程定义(oa_flow,定义管理,不做运行引擎;审批实例走 biz_approval)
 * 状态:1启用 0停用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow")
public class OaFlow extends BaseEntity {

    /** 流程名称 */
    private String flowName;
    /** 关联业务:contract/refund/adjust/decoration */
    private String bizType;
    /** 步骤JSON,如 [{"step":"部门经理"},{"step":"财务"},{"step":"总经理"}] */
    private String steps;
    /** 状态:1启用 0停用 */
    private Integer status;
    /** 备注 */
    private String remark;
}
