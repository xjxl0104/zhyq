ALTER TABLE crm_customer ADD COLUMN fulfillment_requirements JSON NULL COMMENT '结构化仓配需求';
ALTER TABLE crm_customer ADD COLUMN intake_remark VARCHAR(1000) NULL COMMENT '填报人公开备注，不回填旧内部备注';
ALTER TABLE crm_customer
    MODIFY COLUMN contact VARCHAR(100) NULL COMMENT '联系人',
    MODIFY COLUMN industry VARCHAR(100) NULL COMMENT '行业',
    MODIFY COLUMN demand_area VARCHAR(200) NULL COMMENT '需求说明',
    MODIFY COLUMN remark VARCHAR(1000) NULL COMMENT '备注';

-- Serialize the small intake critical section across partners, without adding a
-- unique constraint that could reject historical duplicate customer names.
CREATE TABLE mkt_customer_intake_guard (
    tenant_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mkt_customer_referral_link (
    token CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    promoter_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_referral_partner (promoter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='伙伴专属客户填报入口（独立于伙伴注册邀请码）';

CREATE TABLE mkt_customer_referral_submission (
    token CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    submission_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii NOT NULL,
    result_json JSON NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token, submission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扫码填报幂等回执，不保存客户联系方式';
