-- 保留小程序身份内的账号唯一性，扩展账号实际存储容量；不涉及后台 sys_user。
ALTER TABLE crm_marketing_credential
    MODIFY COLUMN username VARCHAR(512) NOT NULL COMMENT '统一小写，同类身份唯一',
    MODIFY COLUMN password_hash VARCHAR(100) NOT NULL COMMENT '带版本前缀的预哈希 BCrypt，兼容原 BCrypt';
