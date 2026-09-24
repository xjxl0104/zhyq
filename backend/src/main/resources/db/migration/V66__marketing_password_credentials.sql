-- 小程序业务身份独立凭据；不创建或关联 sys_user 管理后台账号。
CREATE TABLE crm_marketing_credential (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    identity_type VARCHAR(2) NOT NULL COMMENT 'mp 伙伴 / wh 云仓',
    identity_id BIGINT NOT NULL COMMENT 'crm_promoter.id 或 crm_warehouse.id',
    username VARCHAR(32) NOT NULL COMMENT '统一小写，同类身份唯一',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt，仅存哈希',
    registration_phone VARCHAR(20) NULL COMMENT '自助注册填写，未经手机验证；不用于登录或合并身份',
    agreed_at DATETIME NULL,
    status TINYINT NOT NULL DEFAULT 1,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_mkt_credential_username (identity_type, username),
    UNIQUE KEY uk_mkt_credential_identity (identity_type, identity_id),
    UNIQUE KEY uk_mkt_credential_phone (identity_type, registration_phone)
) COMMENT '全民营销小程序账号密码凭据';
