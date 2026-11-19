-- Escalation and confirmed in-app delivery were different events in earlier versions.
ALTER TABLE pm_work_order
    ADD COLUMN sla_delivery_confirmed_at DATETIME NULL COMMENT 'SLA站内消息送达确认时间',
    ALGORITHM=INSTANT;
