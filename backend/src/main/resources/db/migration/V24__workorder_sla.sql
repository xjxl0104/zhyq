-- V24: WorkOrder SLA 超时升级 + 回访 (Task #10-T1)

ALTER TABLE pm_work_order
ADD COLUMN resolution_code VARCHAR(32) NULL COMMENT '标准化解决代码',
ADD COLUMN sla_state TINYINT NULL COMMENT 'SLA状态:NULL正常 1响应超时 2解决超时',
ADD COLUMN escalated TINYINT NOT NULL DEFAULT 0 COMMENT '是否已升级通知',
ADD COLUMN revisit_time DATETIME NULL COMMENT '回访时间',
ADD COLUMN revisit_remark VARCHAR(255) NULL COMMENT '回访备注';
