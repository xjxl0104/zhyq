package com.zhyq.park.rule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 事件-联动规则(sys_rule)
 * trigger_type:触发事件类型,首期 ALARM_RAISED
 * condition_json:条件JSON,空/{}=无条件全匹配
 * action_type:动作类型,首期 CREATE_WORKORDER
 * enabled:1启用 0停用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_rule")
public class Rule extends BaseEntity {

    private String name;
    private String triggerType;
    private String conditionJson;
    private String actionType;
    private String actionConfigJson;
    private Integer priority;
    private Integer enabled;
}
