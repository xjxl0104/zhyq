-- V90's first ALTER and CREATE TABLE committed before the collation error.
-- Reapply its remaining DML idempotently, then mark V90 successful only after every statement passes.
UPDATE crm_erp_event_inbox SET next_retry_at=NULL
WHERE deleted=0 AND status='RECEIVED' AND next_retry_at IS NOT NULL;

INSERT IGNORE INTO crm_erp_order_state
    (warehouse_id, order_no, terminal_event, app_id, event_id, occurred_at, project_id, tenant_id)
SELECT warehouse_id, order_no, event, app_id, event_id, occurred_at, project_id, COALESCE(tenant_id,1)
FROM crm_erp_event_inbox
WHERE deleted=0 AND status='PROCESSED' AND event IN ('order.cancelled','order.refunded')
ORDER BY id;

UPDATE crm_erp_event_inbox SET status='WAITING_MAPPING', processed_at=NULL
WHERE deleted=0 AND event='order.shipped' AND status='PROCESSED' AND last_error='UNMAPPED';

UPDATE crm_erp_unmapped u JOIN crm_erp_event_inbox i ON i.id=u.inbox_id
SET u.status='IGNORED'
WHERE u.deleted=0 AND u.status='OPEN' AND i.event<>'order.shipped';

UPDATE crm_erp_event_inbox SET last_error=NULL
WHERE deleted=0 AND status='PROCESSED' AND last_error='UNMAPPED' AND event<>'order.shipped';

UPDATE crm_erp_unmapped u JOIN crm_erp_order_state s
    ON s.warehouse_id=u.warehouse_id
    AND s.order_no COLLATE utf8mb4_unicode_ci=u.order_no COLLATE utf8mb4_unicode_ci
SET u.status='IGNORED',u.replayed_at=CURRENT_TIMESTAMP
WHERE u.deleted=0 AND u.status='OPEN';

UPDATE crm_erp_event_inbox i JOIN crm_erp_order_state s
    ON s.warehouse_id=i.warehouse_id
    AND s.order_no COLLATE utf8mb4_unicode_ci=i.order_no COLLATE utf8mb4_unicode_ci
SET i.status='PROCESSED',i.last_error=NULL,i.processed_at=CURRENT_TIMESTAMP,i.next_retry_at=NULL
WHERE i.deleted=0 AND i.event='order.shipped' AND i.status='WAITING_MAPPING';
