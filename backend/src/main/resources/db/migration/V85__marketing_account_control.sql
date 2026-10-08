-- Dedicated control plane, deliberately excluded from public profile entities/DTOs.
-- No production identity is automatically promoted by migration.
CREATE TABLE crm_marketing_account_control (
  identity_type VARCHAR(2) NOT NULL,
  identity_id BIGINT NOT NULL,
  super_admin TINYINT NOT NULL DEFAULT 0,
  login_disabled TINYINT NOT NULL DEFAULT 0,
  revision BIGINT NOT NULL DEFAULT 1,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (identity_type, identity_id),
  CONSTRAINT chk_marketing_control_type CHECK (identity_type IN ('mp','wh')),
  CONSTRAINT chk_marketing_admin_type CHECK (super_admin = 0 OR identity_type = 'mp')
) COMMENT '小程序账号管理权限与登录停用；只允许受控管理服务写入';
