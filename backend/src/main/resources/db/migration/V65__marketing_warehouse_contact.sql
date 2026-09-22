CREATE TABLE crm_warehouse_contact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL COMMENT '所属云仓',
    openid VARCHAR(64) NULL COMMENT '微信 openid,一个 openid 只能属于一个云仓',
    unionid VARCHAR(64) NULL,
    phone VARCHAR(20) NULL,
    name VARCHAR(64) NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'member' COMMENT 'owner 主联系人 / member 普通联系人',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 正常 0 停用',
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_wh_contact_openid (openid),
    KEY idx_wh_contact_wh (warehouse_id, status)
) COMMENT '云仓联系人(P2-5:一个云仓可绑多个微信/手机号)';

-- 回填:现有 crm_warehouse.contact_openid 各自成为一条 owner 联系人。
-- 旧列保留不删(过渡期兼容),新登录优先查本表,查不到再回落到旧列。
INSERT INTO crm_warehouse_contact (warehouse_id, openid, phone, name, role, status, project_id, tenant_id, create_by, create_time, version, deleted)
SELECT w.id, w.contact_openid, w.phone, w.contact, 'owner', 1, w.project_id, 1, 'migration', NOW(), 1, 0
FROM crm_warehouse w
WHERE w.contact_openid IS NOT NULL AND w.contact_openid <> '' AND w.deleted = 0;
