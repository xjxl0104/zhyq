-- Additive fields only; existing business records are retained.
ALTER TABLE crm_customer
  ADD COLUMN intended_warehouse_id BIGINT NULL COMMENT '伙伴意向云仓',
  ADD COLUMN assigned_warehouse_id BIGINT NULL COMMENT '后台实际分派云仓',
  ADD COLUMN warehouse_assignment_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未分派 1待承接 2已承接 3已拒绝',
  ADD COLUMN public_progress VARCHAR(500) NULL COMMENT '对伙伴公开的进度',
  ADD COLUMN progress_updated_at DATETIME NULL,
  ADD INDEX idx_customer_assigned_warehouse (assigned_warehouse_id, warehouse_assignment_status);

ALTER TABLE crm_warehouse
  ADD COLUMN order_mode VARCHAR(16) NOT NULL DEFAULT 'erp' COMMENT 'erp外部对接/manual人工导入';

ALTER TABLE crm_promoter_account
  ADD COLUMN review_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待人工审核 1通过 2退回',
  ADD COLUMN review_reason VARCHAR(500) NULL,
  ADD COLUMN reviewed_by VARCHAR(100) NULL;

ALTER TABLE crm_withdrawal
  ADD COLUMN account_id BIGINT NULL,
  ADD COLUMN account_name VARCHAR(80) NULL,
  ADD COLUMN account_type TINYINT NULL,
  ADD COLUMN account_no_enc TEXT NULL,
  ADD COLUMN account_tail VARCHAR(8) NULL,
  ADD COLUMN bank_name VARCHAR(120) NULL,
  ADD COLUMN account_verified_at DATETIME NULL,
  ADD COLUMN pay_proof VARCHAR(500) NULL;

ALTER TABLE crm_warehouse_settlement
  ADD COLUMN pay_no VARCHAR(64) NULL,
  ADD COLUMN pay_proof VARCHAR(500) NULL,
  ADD COLUMN paid_at DATETIME NULL,
  ADD COLUMN paid_by VARCHAR(32) NULL,
  ADD UNIQUE KEY uk_warehouse_settlement_pay_no (pay_no);
ALTER TABLE crm_service_fee_bill_line ADD UNIQUE KEY uk_service_fee_order_once (referral_order_id);
ALTER TABLE crm_warehouse_settlement_line ADD UNIQUE KEY uk_warehouse_settle_bill_once (bill_id);

INSERT INTO sys_menu (parent_id,name,type,perm,sort,visible,status,tenant_id,create_by,create_time,version,deleted)
SELECT 0,t.nm,3,t.p,0,0,1,1,'system',NOW(),1,0 FROM (
 SELECT 'crm:marketing:bill:query' p, '全民营销-服务费账单-查询' nm
 UNION ALL SELECT 'crm:marketing:bill:edit','全民营销-服务费账单-生成与复核'
 UNION ALL SELECT 'crm:marketing:settlement:query','全民营销-云仓结算-查询'
 UNION ALL SELECT 'crm:marketing:settlement:edit','全民营销-云仓结算-生成与复核'
 UNION ALL SELECT 'crm:marketing:settlement:pay','全民营销-云仓结算-登记付款'
 UNION ALL SELECT 'crm:marketing:account:audit','全民营销-收款资料-人工审核'
) t WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm=t.p AND m.deleted=0);

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM sys_role r JOIN sys_menu m ON m.deleted=0 AND m.type=3
WHERE r.deleted=0 AND (
 (r.code='admin' AND m.perm IN ('crm:marketing:bill:query','crm:marketing:bill:edit','crm:marketing:settlement:query','crm:marketing:settlement:edit','crm:marketing:settlement:pay','crm:marketing:account:audit'))
 OR (r.code='mkt_ops' AND m.perm IN ('crm:marketing:bill:query','crm:marketing:bill:edit','crm:marketing:settlement:query','crm:marketing:settlement:edit','crm:marketing:account:audit'))
 OR (r.code='mkt_finance' AND m.perm IN ('crm:marketing:bill:query','crm:marketing:settlement:query','crm:marketing:settlement:pay','crm:marketing:account:audit'))
) AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);

ALTER TABLE crm_service_fee_bill
  ADD COLUMN receipt_no VARCHAR(64) NULL,
  ADD COLUMN receipt_proof VARCHAR(500) NULL,
  ADD COLUMN received_at DATETIME NULL,
  ADD COLUMN received_by VARCHAR(32) NULL,
  ADD UNIQUE KEY uk_service_fee_receipt_no(receipt_no);
INSERT INTO sys_menu(parent_id,name,type,perm,sort,visible,status,tenant_id,create_by,create_time,version,deleted)
SELECT 0,'全民营销-服务费账单-收款登记',3,'crm:marketing:bill:pay',0,0,1,1,'system',NOW(),1,0
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perm='crm:marketing:bill:pay' AND deleted=0);
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM sys_role r JOIN sys_menu m ON m.perm='crm:marketing:bill:pay' AND m.deleted=0
WHERE r.code IN ('admin','mkt_finance') AND r.deleted=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);

ALTER TABLE crm_direct_sign_payment ADD COLUMN pay_proof VARCHAR(500) NULL;
