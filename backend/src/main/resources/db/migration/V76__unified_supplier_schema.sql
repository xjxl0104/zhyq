-- Expand first: keep legacy responsibility links available during the transition.
ALTER TABLE pur_supplier
    ADD COLUMN unit_type VARCHAR(32) NULL COMMENT '原责任单位类型',
    ADD COLUMN service_scope VARCHAR(255) NULL COMMENT '服务范围',
    ADD COLUMN project_id BIGINT NULL COMMENT '所属园区',
    MODIFY COLUMN remark VARCHAR(500);

ALTER TABLE pm_responsible_unit
    ADD COLUMN supplier_id BIGINT NULL COMMENT '合并后的供应商档案 ID';

ALTER TABLE pm_work_order
    ADD COLUMN supplier_id BIGINT NULL COMMENT '承接供应商 pur_supplier.id';
CREATE INDEX idx_wo_supplier ON pm_work_order (supplier_id);

ALTER TABLE pur_supplier_contract
    ADD COLUMN parent_contract_id BIGINT NULL COMMENT '补充协议对应的主合同 ID';
CREATE INDEX idx_sc_parent ON pur_supplier_contract (parent_contract_id);
