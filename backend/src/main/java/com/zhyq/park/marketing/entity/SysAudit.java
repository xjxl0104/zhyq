package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 通用业务审计(表 sys_audit,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_audit")
public class SysAudit extends BaseEntity {
    /** 模块:crm.marketing 等 */
    private String module;
    /** 动作:promoter.parent.change / commission.void … */
    private String action;
    /** 业务对象类型 */
    private String bizType;
    /** 业务对象 id */
    private Long bizId;
    /** 操作原因(改上级/作废/调岗等必填) */
    private String reason;
    /** 变更前快照 */
    private String beforeJson;
    /** 变更后快照 */
    private String afterJson;
    /** 操作人 */
    private String operator;
    private String ip;
    private Long projectId;
}
