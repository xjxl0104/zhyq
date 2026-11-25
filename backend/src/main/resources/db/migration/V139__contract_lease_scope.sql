-- =====================================================================
-- V139: 正式合同(biz_contract)新增租赁区域/范围字段(lease_scope)
-- =====================================================================
ALTER TABLE biz_contract
    ADD COLUMN lease_scope VARCHAR(255) NULL COMMENT '租赁区域/范围' AFTER project_id;
