CREATE TABLE crm_service_fee_bill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    billing_key VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 2 COMMENT '1草稿 2待确认 3已确认 4已结算 5争议',
    amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    dispute_reason VARCHAR(500) NULL,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_service_fee_bill_key (billing_key),
    KEY idx_service_fee_bill_wh (warehouse_id, period_start, period_end)
) COMMENT '全民营销服务费月度账单';

CREATE TABLE crm_service_fee_bill_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_id BIGINT NOT NULL,
    referral_order_id BIGINT NULL,
    source_type TINYINT NULL,
    source_no VARCHAR(64) NULL,
    amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    snapshot_json JSON NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_service_fee_line (bill_id, referral_order_id)
) COMMENT '服务费账单明细';
