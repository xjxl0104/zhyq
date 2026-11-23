-- Preserve the original warehouse contact as the sole owner. Other historical
-- contacts remain members; no contact, history, warehouse or customer is deleted.
UPDATE crm_warehouse_contact c
JOIN (
  SELECT warehouse_id, COALESCE(MIN(original_id), MIN(owner_id)) owner_id
  FROM (
    SELECT c1.warehouse_id,
           CASE WHEN w.contact_openid IS NOT NULL AND w.contact_openid <> ''
                     AND c1.openid = w.contact_openid THEN c1.id END original_id,
           CASE WHEN c1.role = 'owner' THEN c1.id END owner_id
    FROM crm_warehouse_contact c1
    JOIN crm_warehouse w ON w.id=c1.warehouse_id AND w.deleted=0
    WHERE c1.deleted=0
  ) candidates GROUP BY warehouse_id
) canonical ON canonical.warehouse_id=c.warehouse_id
SET c.role=CASE WHEN c.id=canonical.owner_id THEN 'owner' ELSE 'member' END
WHERE c.deleted=0 AND canonical.owner_id IS NOT NULL;

ALTER TABLE crm_warehouse_contact
  ADD COLUMN auth_revision BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN phone_binding_approved TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN password_reset_required TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN owner_warehouse_key BIGINT GENERATED ALWAYS AS
    (CASE WHEN role='owner' AND deleted=0 THEN warehouse_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_wh_one_owner(owner_warehouse_key),
  ADD INDEX idx_wh_contact_phone(phone,deleted);

-- Older WeChat owners may have an empty contact profile while the warehouse
-- was completed by operations. Only the same established openid may inherit
-- missing fields; nonempty phone/name conflicts are deliberately not rewritten.
UPDATE crm_warehouse_contact c
JOIN crm_warehouse w ON w.id=c.warehouse_id AND w.deleted=0
SET c.phone=CASE WHEN (c.phone IS NULL OR TRIM(c.phone)='')
                    AND w.phone REGEXP '^1[0-9]{10}$' THEN w.phone ELSE c.phone END,
    c.name=CASE WHEN (c.name IS NULL OR TRIM(c.name)='')
                    AND w.contact IS NOT NULL AND TRIM(w.contact)<>'' THEN w.contact ELSE c.name END
WHERE c.deleted=0 AND c.role='owner' AND c.openid IS NOT NULL AND c.openid<>''
  AND c.openid=w.contact_openid;

-- Serializes phone claims across different warehouses without touching partner identities.
CREATE TABLE crm_warehouse_phone_lock (
  phone VARCHAR(20) PRIMARY KEY
) COMMENT '云仓身份手机号认领互斥，不参与伙伴手机号唯一性';

ALTER TABLE crm_customer
  ADD COLUMN warehouse_salesperson_id BIGINT NULL COMMENT '云仓内负责业务员联系人ID',
  ADD COLUMN warehouse_sales_stage TINYINT NOT NULL DEFAULT 0 COMMENT '0待承接1业务员承接2价格谈判3签约4货品入仓5正式发货',
  ADD INDEX idx_customer_wh_salesperson(assigned_warehouse_id,warehouse_salesperson_id);

CREATE TABLE crm_warehouse_customer_progress (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  warehouse_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  salesperson_id BIGINT NULL,
  actor_contact_id BIGINT NOT NULL,
  actor_name VARCHAR(64) NOT NULL,
  stage TINYINT NOT NULL,
  summary VARCHAR(500) NOT NULL,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL,
  KEY idx_wh_customer_progress(warehouse_id,customer_id,tenant_id,create_time,id)
) COMMENT '云仓业务员客户进度追加历史';
