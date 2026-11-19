CREATE TABLE iot_alarm_workorder_action (
    alarm_id BIGINT PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    action_config TEXT,
    space_id BIGINT,
    next_attempt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    KEY idx_alarm_action_due (completed_at, next_attempt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备告警自动建单持久任务';
