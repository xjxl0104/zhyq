-- ERP 入站事件可靠性：幂等 Inbox、死信和未映射补偿队列。
CREATE TABLE crm_erp_event_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    app_id VARCHAR(32) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event VARCHAR(32) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    payload_json JSON NULL,
    occurred_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    attempts INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL,
    last_error VARCHAR(500) NULL,
    processed_at DATETIME NULL,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_erp_inbox_event (warehouse_id, app_id, event_id),
    KEY idx_erp_inbox_status (status, next_retry_at),
    KEY idx_erp_inbox_order (warehouse_id, order_no)
) COMMENT 'ERP 入站事件幂等 Inbox';

CREATE TABLE crm_erp_dead_letter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inbox_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    error_code VARCHAR(32) NOT NULL,
    error_message VARCHAR(500) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    replayed_at DATETIME NULL,
    replayed_by VARCHAR(32) NULL,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_erp_dead_inbox (inbox_id)
) COMMENT 'ERP 事件死信';

CREATE TABLE crm_erp_unmapped (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    customer_code VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    inbox_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    payload_json JSON NULL,
    mapped_customer_id BIGINT NULL,
    replayed_at DATETIME NULL,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_erp_unmapped_event (warehouse_id, event_id),
    KEY idx_erp_unmapped_status (warehouse_id, status)
) COMMENT 'ERP 未映射货主补偿队列';

CREATE TABLE crm_erp_reconcile_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    expected_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    actual_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    diff_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    diff_ratio DECIMAL(10,6) NOT NULL DEFAULT 0,
    frozen TINYINT NOT NULL DEFAULT 0,
    reason VARCHAR(500) NULL,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_erp_reconcile_period (warehouse_id, period_start, period_end)
) COMMENT 'ERP 对账不可变快照';
