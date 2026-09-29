-- 客户每单自定义定价。保留历史配置/流水；不根据等级默认金额回填。
CREATE TABLE crm_customer_pricing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    owner_promoter_id BIGINT NOT NULL COMMENT '配置该版本的 P4',
    total_per_order DECIMAL(14,3) NOT NULL,
    owner_per_order DECIMAL(14,3) NOT NULL COMMENT 'P4 本人金额，显式配置',
    beneficiaries_json JSON NOT NULL COMMENT '最多2名其他受益人及每单金额快照',
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_customer_pricing_latest (customer_id, id),
    KEY idx_customer_pricing_owner (owner_promoter_id)
) COMMENT 'P4 客户每单定价版本，新增版本不改历史订单';

ALTER TABLE crm_referral_order
    ADD COLUMN pricing_id BIGINT NULL COMMENT '自定义每单定价版本，历史比例单为空',
    ADD COLUMN commission_unit_price DECIMAL(14,3) NULL COMMENT '每单总额，含公司留存',
    ADD COLUMN commission_order_count INT NULL COMMENT '每个唯一出库单号计1单',
    ADD COLUMN company_per_order DECIMAL(14,3) NULL COMMENT '公司留存，不入个人佣金',
    MODIFY COLUMN pool_amount DECIMAL(15,3) NOT NULL DEFAULT 0 COMMENT '个人佣金池，不含公司留存';

ALTER TABLE crm_promoter_commission
    ADD COLUMN pricing_id BIGINT NULL,
    ADD COLUMN amount_per_order DECIMAL(14,3) NULL,
    ADD COLUMN order_count INT NULL,
    MODIFY COLUMN amount DECIMAL(15,3) NOT NULL COMMENT '个人佣金精确到0.001元，负向为扣回';
ALTER TABLE crm_settle_batch
    MODIFY COLUMN total_amount DECIMAL(15,3) NOT NULL DEFAULT 0;
