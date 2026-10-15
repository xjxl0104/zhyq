-- A signed heartbeat is independent from the last business event.
-- Existing credentials must sign a new request; old order timestamps are not proof of a live connection.
ALTER TABLE crm_warehouse_erp
    ADD COLUMN last_heartbeat_at DATETIME NULL COMMENT '最近验签成功时间' AFTER last_sync_at,
    ADD COLUMN connection_revision INT NOT NULL DEFAULT 0 COMMENT '凭证连接配置修订号' AFTER last_heartbeat_at;

UPDATE crm_warehouse SET erp_status = 0 WHERE deleted = 0 AND erp_status <> 0;
