-- =====================================================================
-- V33 ver4.3 自动售货机安全入口与本地经营数据副本
-- =====================================================================

CREATE TABLE ops_vending_machine (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_machine_id VARCHAR(80) NOT NULL,
    machine_name VARCHAR(128),
    site_name VARCHAR(255),
    model VARCHAR(128),
    running_status VARCHAR(40),
    last_online_time DATETIME,
    source_batch_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_vending_machine (tenant_id, vendor_machine_id, deleted),
    KEY idx_vending_machine_batch (source_batch_id),
    KEY idx_vending_machine_status (running_status)
) COMMENT '自动售货机本地副本';

CREATE TABLE ops_vending_sale (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_order_id VARCHAR(100) NOT NULL,
    line_no INT NOT NULL,
    vendor_machine_id VARCHAR(80) NOT NULL,
    product_id VARCHAR(100),
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    original_amount DECIMAL(16,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(16,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(16,2) NOT NULL DEFAULT 0,
    payment_method VARCHAR(64),
    payment_time DATETIME NOT NULL,
    order_status VARCHAR(40) NOT NULL,
    source_batch_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_vending_sale (tenant_id, vendor_order_id, line_no, deleted),
    KEY idx_vending_sale_batch (source_batch_id),
    KEY idx_vending_sale_time (payment_time),
    KEY idx_vending_sale_machine (vendor_machine_id)
) COMMENT '自动售货机销售记录';

CREATE TABLE ops_vending_restock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_restock_id VARCHAR(100) NOT NULL,
    vendor_machine_id VARCHAR(80) NOT NULL,
    product_id VARCHAR(100),
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    operator_name VARCHAR(128),
    restock_time DATETIME NOT NULL,
    source_batch_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_vending_restock (tenant_id, vendor_restock_id, deleted),
    KEY idx_vending_restock_batch (source_batch_id),
    KEY idx_vending_restock_time (restock_time)
) COMMENT '自动售货机补货记录';

CREATE TABLE ops_vending_fault (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_fault_id VARCHAR(100) NOT NULL,
    vendor_machine_id VARCHAR(80) NOT NULL,
    fault_type VARCHAR(100) NOT NULL,
    occurred_time DATETIME NOT NULL,
    recovered_time DATETIME,
    fault_status VARCHAR(40) NOT NULL,
    description VARCHAR(1000),
    source_batch_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_vending_fault (tenant_id, vendor_fault_id, deleted),
    KEY idx_vending_fault_batch (source_batch_id),
    KEY idx_vending_fault_status (fault_status)
) COMMENT '自动售货机故障记录';

CREATE TABLE ops_vending_reconciliation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_settlement_id VARCHAR(100) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    sales_amount DECIMAL(16,2) NOT NULL DEFAULT 0,
    refund_amount DECIMAL(16,2) NOT NULL DEFAULT 0,
    platform_fee DECIMAL(16,2) NOT NULL DEFAULT 0,
    net_amount DECIMAL(16,2) NOT NULL DEFAULT 0,
    settlement_status VARCHAR(40) NOT NULL,
    source_batch_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_vending_reconciliation (tenant_id, vendor_settlement_id, deleted),
    KEY idx_vending_reconciliation_batch (source_batch_id),
    KEY idx_vending_reconciliation_period (period_start, period_end)
) COMMENT '自动售货机对账记录';

INSERT INTO sys_app (name, category, icon, path, description, sort, status, tenant_id,
                     create_by, create_time, version, deleted)
SELECT '自动售货机', '园区运营', 'ShoppingCart', '/app/vending',
       '自动售货机入口与经营数据', 17, 1, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_app WHERE path = '/app/vending' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id,
                      create_by, create_time, version, deleted)
SELECT 0, p.name, 3, p.perm, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'vending:query' perm, '自动售货机-查询' name
    UNION ALL SELECT 'vending:import', '自动售货机-导入'
    UNION ALL SELECT 'vending:open', '自动售货机-打开厂商系统'
    UNION ALL SELECT 'vending:config', '自动售货机-配置'
) p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.perm = p.perm AND m.deleted = 0
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perm LIKE 'vending:%' AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
