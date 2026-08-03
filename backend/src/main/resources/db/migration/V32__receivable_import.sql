-- =====================================================================
-- V32 ver4.3 受控导入 + 应收明细登记表
-- =====================================================================

CREATE TABLE sys_import_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type VARCHAR(40) NOT NULL,
    source_system VARCHAR(40) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_hash CHAR(64) NOT NULL,
    file_id BIGINT,
    status VARCHAR(24) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    imported_rows INT NOT NULL DEFAULT 0,
    error_summary VARCHAR(1000),
    confirmed_by VARCHAR(64),
    confirmed_time DATETIME,
    rollback_by VARCHAR(64),
    rollback_time DATETIME,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_import_file (tenant_id, biz_type, file_hash, deleted),
    KEY idx_import_status (biz_type, status)
) COMMENT '受控导入批次';

CREATE TABLE sys_import_row (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    sheet_name VARCHAR(128),
    row_no INT NOT NULL,
    row_fingerprint CHAR(64) NOT NULL,
    raw_json LONGTEXT NOT NULL,
    normalized_json LONGTEXT,
    status VARCHAR(24) NOT NULL,
    error_message VARCHAR(1000),
    target_type VARCHAR(64),
    target_id BIGINT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_import_row (batch_id, row_no, deleted),
    KEY idx_import_row_fp (row_fingerprint),
    KEY idx_import_row_status (batch_id, status)
) COMMENT '受控导入行';

CREATE TABLE fin_collection_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_type VARCHAR(32) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    account_no_cipher TEXT NOT NULL,
    account_no_masked VARCHAR(64) NOT NULL,
    account_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_collection_account (tenant_id, account_fingerprint, deleted)
) COMMENT '财务收款账户';

CREATE TABLE fin_receivable_register (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    internal_code VARCHAR(64) NOT NULL,
    business_key CHAR(64) NOT NULL,
    seq_no INT,
    agreement_no_raw TEXT,
    tenant_name_raw VARCHAR(255) NOT NULL,
    space_name_raw VARCHAR(255) NOT NULL,
    charge_area DECIMAL(14,4) NOT NULL DEFAULT 0,
    actual_area DECIMAL(14,4) NOT NULL DEFAULT 0,
    shared_area DECIMAL(14,4) NOT NULL DEFAULT 0,
    contract_term_raw VARCHAR(64),
    contract_rent_total DECIMAL(20,6) NOT NULL DEFAULT 0,
    contract_period_raw VARCHAR(255),
    contract_start_date DATE,
    contract_end_date DATE,
    escalation_raw VARCHAR(255),
    free_term_raw VARCHAR(64),
    free_period_raw TEXT,
    discount_raw TEXT,
    rent_rate_raw VARCHAR(255),
    property_rate_raw VARCHAR(255),
    monthly_rent DECIMAL(16,2) NOT NULL DEFAULT 0,
    monthly_property DECIMAL(16,2) NOT NULL DEFAULT 0,
    monthly_total DECIMAL(16,2) NOT NULL DEFAULT 0,
    rent_deposit DECIMAL(16,2) NOT NULL DEFAULT 0,
    property_deposit DECIMAL(16,2) NOT NULL DEFAULT 0,
    collection_timing_raw TEXT,
    first_collection_raw VARCHAR(255),
    rent_account_id BIGINT,
    property_account_id BIGINT,
    rent_account_masked VARCHAR(500),
    property_account_masked VARCHAR(500),
    notes_raw TEXT,
    deposit_difference DECIMAL(16,2),
    tenant_ref_id BIGINT,
    space_id BIGINT,
    room_id BIGINT,
    contract_id BIGINT,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    source_batch_id BIGINT,
    source_row_id BIGINT,
    source_version INT NOT NULL DEFAULT 1,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    active_unique_key TINYINT GENERATED ALWAYS AS (IF(deleted = 0, 1, NULL)) STORED,
    UNIQUE KEY uk_receivable_code (internal_code, active_unique_key),
    UNIQUE KEY uk_receivable_business (tenant_id, business_key, active_unique_key),
    KEY idx_receivable_tenant (tenant_ref_id),
    KEY idx_receivable_contract (contract_id),
    KEY idx_receivable_status (status),
    KEY idx_receivable_source (source_batch_id, source_row_id)
) COMMENT '应收明细登记表';

CREATE TABLE fin_receivable_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    register_id BIGINT NOT NULL,
    fee_type VARCHAR(32) NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    effective_start DATE,
    effective_end DATE,
    rate_unit VARCHAR(32),
    rate_value DECIMAL(16,6),
    fixed_amount DECIMAL(16,2),
    discount_rate DECIMAL(8,4),
    interval_years INT,
    increase_rate DECIMAL(8,4),
    recurrence_rule VARCHAR(64),
    apply_scope VARCHAR(64),
    priority INT NOT NULL DEFAULT 100,
    raw_text TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_receivable_rule (register_id, fee_type, priority)
) COMMENT '应收计费规则';

CREATE TABLE fin_deposit_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    register_id BIGINT NOT NULL,
    deposit_type VARCHAR(32) NOT NULL,
    required_amount DECIMAL(16,2) NOT NULL DEFAULT 0,
    confirmed_received_amount DECIMAL(16,2) NOT NULL DEFAULT 0,
    difference_amount DECIMAL(16,2) NOT NULL DEFAULT 0,
    source_difference_amount DECIMAL(16,2),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    active_unique_key TINYINT GENERATED ALWAYS AS (IF(deleted = 0, 1, NULL)) STORED,
    UNIQUE KEY uk_deposit_ledger (register_id, deposit_type, active_unique_key)
) COMMENT '保证金台账';

ALTER TABLE fin_bill
    ADD COLUMN receivable_register_id BIGINT NULL AFTER contract_id,
    ADD COLUMN receivable_rule_id BIGINT NULL AFTER receivable_register_id,
    ADD COLUMN billing_key VARCHAR(160) NULL AFTER code,
    ADD COLUMN billing_active_key VARCHAR(160)
        GENERATED ALWAYS AS (IF(deleted = 0, billing_key, NULL)) STORED AFTER billing_key,
    ADD UNIQUE KEY uk_bill_billing_key (billing_active_key),
    ADD KEY idx_bill_receivable (receivable_register_id);

INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, p.name, 3, p.perm, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'finance:receivable:query' perm, '应收明细-查询' name
    UNION ALL SELECT 'finance:receivable:add', '应收明细-新增'
    UNION ALL SELECT 'finance:receivable:edit', '应收明细-修改'
    UNION ALL SELECT 'finance:receivable:import', '应收明细-导入'
    UNION ALL SELECT 'finance:receivable:confirm', '应收明细-确认'
    UNION ALL SELECT 'finance:receivable:generate', '应收明细-生成账单'
    UNION ALL SELECT 'finance:receivable:export', '应收明细-导出'
    UNION ALL SELECT 'finance:receivable:delete', '应收明细-删除'
    UNION ALL SELECT 'finance:receivable:account:view', '应收明细-查看账户'
) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm = p.perm AND m.deleted = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perm LIKE 'finance:receivable:%' AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
