CREATE TABLE crm_competition_protection (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  warehouse_id BIGINT NOT NULL,
  name_enc VARCHAR(1024) NOT NULL COMMENT 'AES-GCM encrypted brand/company name',
  name_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Keyed HMAC lookup',
  create_by VARCHAR(64), create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64), update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version INT NOT NULL DEFAULT 1, deleted INT NOT NULL DEFAULT 0,
  active_fingerprint CHAR(64) GENERATED ALWAYS AS (IF(deleted = 0, name_fingerprint, NULL)) STORED,
  UNIQUE KEY uk_protection_owner (tenant_id, warehouse_id, active_fingerprint),
  KEY idx_protection_match (tenant_id, name_fingerprint, deleted)
) COMMENT 'Confidential warehouse competition protection; no public list';

ALTER TABLE crm_customer ADD COLUMN brand_name VARCHAR(100) NULL COMMENT 'Optional referral brand for protection matching';
