-- =====================================================================
-- V134 公海批量导入后台异步化、信用代码去重索引与专员在途上限
--   1. crm_public_sea_wash_batch 增加处理状态与进度列（大批量导入改后台执行，前端轮询进度）；
--   2. crm_public_sea_meta 增加 (tenant_id, credit_code) 索引，用于按统一社会信用代码去重；
--   3. 预置内部专员在途认领上限参数 sea_admin_active_cap（默认 30 户）。
--   零 ALTER crm_customer。
-- =====================================================================

ALTER TABLE crm_public_sea_wash_batch
    ADD COLUMN status         VARCHAR(16)  NOT NULL DEFAULT 'DONE' COMMENT 'RUNNING/DONE/FAILED',
    ADD COLUMN processed_rows INT          NOT NULL DEFAULT 0 COMMENT '已处理行数（后台导入进度）',
    ADD COLUMN error_msg      VARCHAR(255) NULL COMMENT '后台导入失败原因',
    ADD COLUMN finished_at    DATETIME     NULL COMMENT '后台导入完成时间',
    ADD KEY idx_wash_batch_no (batch_no);

ALTER TABLE crm_public_sea_meta
    ADD KEY idx_sea_credit_code (tenant_id, credit_code);

INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted)
SELECT 'marketing', 'sea_admin_active_cap', '30', '内部招商专员同时在跟的公海客户上限（户），防止囤积客户', 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM biz_setting b WHERE b.module = 'marketing' AND b.skey = 'sea_admin_active_cap' AND b.deleted = 0);
