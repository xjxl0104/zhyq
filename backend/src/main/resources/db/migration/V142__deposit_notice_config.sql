-- V142: 保证金缴费通知单抬头与收取单位配置(分昌记租金保证金 / 数智云仓物业保证金)
CREATE TABLE IF NOT EXISTS eng_deposit_config (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    park_name                VARCHAR(128)  NULL COMMENT '园区名称',
    notice_prefix            VARCHAR(32)   NULL COMMENT '单据编号前缀',
    issue_date               DATE          NULL COMMENT '开具日期',
    due_date                 DATE          NULL COMMENT '缴费截止日',
    rent_payee_name          VARCHAR(128)  NULL COMMENT '租金保证金收取单位(如广州市昌记供应链有限公司)',
    rent_payment_account     VARCHAR(512)  NULL COMMENT '租金保证金收款账户',
    rent_contact_name        VARCHAR(64)   NULL COMMENT '租金保证金联系人',
    rent_contact_phone       VARCHAR(32)   NULL COMMENT '租金保证金联系电话',
    property_payee_name      VARCHAR(128)  NULL COMMENT '物业保证金收取单位(如广州数智云仓产业园运营有限公司)',
    property_payment_account VARCHAR(512)  NULL COMMENT '物业保证金收款账户',
    property_contact_name    VARCHAR(64)   NULL COMMENT '物业保证金联系人',
    property_contact_phone   VARCHAR(32)   NULL COMMENT '物业保证金联系电话',
    update_time              DATETIME      NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '保证金缴费通知单抬头与收取单位配置';
