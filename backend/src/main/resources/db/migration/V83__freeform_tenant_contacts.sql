-- Contact records are independent from the system tenant directory. Existing contacts are copied,
-- while biz_tenant and historical work-order snapshots remain untouched.
CREATE TABLE pur_tenant_contact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_name VARCHAR(128) NOT NULL COMMENT '手动录入的租客名称',
    project_id BIGINT NULL COMMENT '所属园区，可不填',
    tenant_ref_id BIGINT NULL COMMENT '迁移前的租客档案关联，仅供历史追溯',
    contact VARCHAR(64) NOT NULL COMMENT '联系人',
    phone VARCHAR(20) NULL COMMENT '联系电话',
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME,
    update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_tenant_contact_name (tenant_name),
    KEY idx_tenant_contact_project (project_id)
) COMMENT '自由维护的租客联系人';

ALTER TABLE pm_work_order
    ADD COLUMN tenant_name VARCHAR(128) NULL COMMENT '建单时的租客名称快照',
    ADD COLUMN tenant_contact_ref_id BIGINT NULL COMMENT '关联自由维护的租客联系人记录',
    ADD INDEX idx_work_order_tenant_contact_ref (tenant_contact_ref_id);
