-- The warehouse row lock serializes ERP events and manual imports on every application node.
ALTER TABLE crm_erp_unmapped DROP INDEX uk_erp_unmapped_event,
    ADD UNIQUE KEY uk_erp_unmapped_inbox (inbox_id);

-- Older updateById transitions left next_retry_at populated after switching to RECEIVED.
UPDATE crm_erp_event_inbox SET next_retry_at = NULL
WHERE deleted = 0 AND status = 'RECEIVED' AND next_retry_at IS NOT NULL;

CREATE TABLE crm_erp_order_state (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    terminal_event VARCHAR(32) NOT NULL,
    app_id VARCHAR(128) NULL,
    event_id VARCHAR(256) NULL,
    occurred_at DATETIME NULL,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64) NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    deleted INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_erp_order_terminal (warehouse_id, order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP cancelled/refunded order tombstones';

-- Recover valid cancellations already accepted before this fix, including cancel-before-shipment.
INSERT IGNORE INTO crm_erp_order_state
    (warehouse_id, order_no, terminal_event, app_id, event_id, occurred_at, project_id, tenant_id)
SELECT warehouse_id, order_no, event, app_id, event_id, occurred_at, project_id, COALESCE(tenant_id, 1)
FROM crm_erp_event_inbox
WHERE deleted = 0 AND status = 'PROCESSED' AND event IN ('order.cancelled', 'order.refunded')
ORDER BY id;

UPDATE crm_erp_event_inbox SET status = 'WAITING_MAPPING', processed_at = NULL
WHERE deleted = 0 AND event = 'order.shipped' AND status = 'PROCESSED' AND last_error = 'UNMAPPED';

-- Created/paid/terminal events never needed a customer mapping to be processed.
UPDATE crm_erp_unmapped u JOIN crm_erp_event_inbox i ON i.id = u.inbox_id
SET u.status = 'IGNORED'
WHERE u.deleted = 0 AND u.status = 'OPEN' AND i.event <> 'order.shipped';

UPDATE crm_erp_event_inbox SET last_error = NULL
WHERE deleted = 0 AND status = 'PROCESSED' AND last_error = 'UNMAPPED' AND event <> 'order.shipped';

-- Terminal shipments require no customer mapping; keep them out of the compensation backlog.
UPDATE crm_erp_unmapped u JOIN crm_erp_order_state s ON s.warehouse_id = u.warehouse_id AND s.order_no = u.order_no
SET u.status = 'IGNORED', u.replayed_at = CURRENT_TIMESTAMP
WHERE u.deleted = 0 AND u.status = 'OPEN';

UPDATE crm_erp_event_inbox i JOIN crm_erp_order_state s ON s.warehouse_id = i.warehouse_id AND s.order_no = i.order_no
SET i.status = 'PROCESSED', i.last_error = NULL, i.processed_at = CURRENT_TIMESTAMP, i.next_retry_at = NULL
WHERE i.deleted = 0 AND i.event = 'order.shipped' AND i.status = 'WAITING_MAPPING';
