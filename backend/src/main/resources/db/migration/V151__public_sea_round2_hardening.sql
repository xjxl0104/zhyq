-- =====================================================================
-- V135 公海池与连接器第二轮加固：多轮跟进留痕表、索引补齐与最长跟进回收参数
-- 设计约束：
--   1. 零 ALTER crm_customer 存量核心表；
--   2. 新增 crm_public_sea_followup 记录多次电联/看仓/报价跟进时间线；
--   3. 补齐专员在跟上限、分区排序与运单防重抽检联合索引；
--   4. 预置最长跟进未签约回收天数 sea_followup_max_days = 30。
-- =====================================================================

CREATE TABLE IF NOT EXISTS crm_public_sea_followup (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id      BIGINT       NOT NULL COMMENT '关联 crm_customer.id',
    tenant_id        BIGINT       NOT NULL DEFAULT 1,
    project_id       BIGINT       NULL COMMENT '所属园区项目ID',
    action_type      VARCHAR(24)  NOT NULL DEFAULT 'CALL' COMMENT 'CALL/VISIT/WECHAT/QUOTE/ASSIGN/ADOPT_VERIFY',
    note             VARCHAR(500) NOT NULL COMMENT '跟进沟通纪要',
    next_followup_at DATETIME     NULL COMMENT '下次计划跟进时间',
    operator         VARCHAR(64)  NOT NULL COMMENT '跟进人账号',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sea_followup_cust (customer_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='内部公海客户多轮跟进时间线留痕表';

ALTER TABLE crm_public_sea_meta
    ADD KEY idx_sea_admin_cap (tenant_id, claim_status, claim_holder_name),
    ADD KEY idx_sea_zone_sort (tenant_id, claim_status, zone_code, urgency_level, updated_at);

ALTER TABLE crm_waybill_spot_check
    ADD KEY idx_waybill_check_order (tenant_id, settle_month, order_id);

INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted)
SELECT 'marketing', 'sea_followup_max_days', '30', '公海客户完成首触达或15天地推保护期满后最长跟进天数（超期未签约且无新跟进自动回流公海）', 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_setting b WHERE b.module = 'marketing' AND b.skey = 'sea_followup_max_days' AND b.deleted = 0);
