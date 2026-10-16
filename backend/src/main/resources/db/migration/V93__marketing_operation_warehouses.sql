-- 本地作业仓库与客户建档独立于外部接口授权；不修改或迁移历史接口映射。
CREATE TABLE crm_operation_warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL COMMENT '所属加盟云仓',
    name VARCHAR(128) NOT NULL COMMENT '实际作业仓库名称',
    address VARCHAR(300) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_operation_warehouse_name (tenant_id, warehouse_id, name)
) COMMENT '加盟云仓的本地作业仓库档案';

CREATE TABLE crm_customer_operation_warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL COMMENT '所属加盟云仓',
    customer_id BIGINT NOT NULL COMMENT '独立园区客户',
    operation_warehouse_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_customer_operation_warehouse (tenant_id, customer_id, operation_warehouse_id),
    KEY idx_customer_operation_wh (tenant_id, warehouse_id, id)
) COMMENT '客户与本地作业仓库关联，不等于外部接口授权';
