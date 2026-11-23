-- 本迁移只扩展快照与审计结构；历史订单由带备份、幂等校验和逐行审计的重算工具受控处理。
ALTER TABLE crm_referral_order
    ADD COLUMN platform_service_fee_per_order DECIMAL(15,4) NULL COMMENT '平台服务费每单快照，不含未分配余额',
    ADD COLUMN unallocated_per_order DECIMAL(15,4) NULL COMMENT '扣除平台服务费及个人分配后的余额';

CREATE TABLE crm_platform_fee_reprice_run (
    tenant_id BIGINT NOT NULL,
    run_key VARCHAR(64) NOT NULL,
    summary_json JSON NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, run_key)
) COMMENT '平台服务费历史重算幂等记录';

CREATE TABLE crm_platform_fee_reprice_audit (
    tenant_id BIGINT NOT NULL,
    run_key VARCHAR(64) NOT NULL,
    table_name VARCHAR(64) NOT NULL,
    row_id BIGINT NOT NULL,
    before_json JSON NOT NULL,
    after_json JSON NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, run_key, table_name, row_id)
) COMMENT '平台服务费历史重算逐行变更审计';
