CREATE TABLE crm_warehouse_settlement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status TINYINT NOT NULL DEFAULT 2 COMMENT '1草稿 2待确认 3已确认 4已支付 5争议 6冻结',
    amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    frozen_reason VARCHAR(500) NULL,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_warehouse_settle_period (warehouse_id, period_start, period_end),
    UNIQUE KEY uk_warehouse_settle_batch (batch_no)
) COMMENT '云仓结算快照';

CREATE TABLE crm_warehouse_settlement_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_id BIGINT NOT NULL,
    bill_id BIGINT NULL,
    referral_order_id BIGINT NULL,
    amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    snapshot_json JSON NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_settlement_line (settlement_id, bill_id, referral_order_id)
) COMMENT '云仓结算明细';

CREATE TABLE crm_direct_sign_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    payment_no VARCHAR(64) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1已到账 2拒绝',
    paid_at DATETIME NULL,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_direct_sign_payment_no (payment_no)
) COMMENT '直签平台费到账';
