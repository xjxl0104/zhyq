-- V22__rule_center.sql 事件-联动规则中心（#8）
-- 新建 sys_rule 表 + 工单防重列

-- 规则表（事件触发→条件判定→动作执行）
CREATE TABLE sys_rule (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(64)  NOT NULL COMMENT '规则名',
    trigger_type        VARCHAR(32)  NOT NULL COMMENT '触发事件类型（ALARM_RAISED等）',
    condition_json      VARCHAR(512) NOT NULL DEFAULT '{}' COMMENT '条件JSON（如 {"minLevel":2,"deviceType":"fire"}）',
    action_type         VARCHAR(32)  NOT NULL COMMENT '动作类型（CREATE_WORKORDER等）',
    action_config_json  VARCHAR(512) NOT NULL COMMENT '动作配置JSON（如 {"orderType":"repair","title":"告警工单"}）',
    priority            INT          NOT NULL DEFAULT 100 COMMENT '执行优先级（小先）',
    enabled             TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    tenant_id           BIGINT       NULL,
    create_by           VARCHAR(64)  NULL,
    create_time         DATETIME     NULL,
    update_by           VARCHAR(64)  NULL,
    update_time         DATETIME     NULL,
    version             INT          NOT NULL DEFAULT 0,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_trigger (trigger_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件-联动规则';

-- 种子规则：告警自动建单
INSERT INTO sys_rule (
    name, trigger_type, condition_json, action_type, action_config_json,
    priority, enabled, create_by, create_time, version, deleted
) VALUES (
    '告警自动建单',
    'ALARM_RAISED',
    '{}',
    'CREATE_WORKORDER',
    '{\"orderType\":\"repair\",\"title\":\"IoT告警工单\"}',
    100,
    1,
    'system',
    NOW(),
    0,
    0
);

-- 工单防重：同一告警同一规则不重复建单
ALTER TABLE pm_work_order
ADD COLUMN source_alarm_id BIGINT NULL COMMENT '来源告警id(防重)',
ADD KEY idx_source_alarm (source_alarm_id);
