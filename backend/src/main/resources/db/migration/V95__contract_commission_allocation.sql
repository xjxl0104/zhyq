-- 按合同佣金分配(P4 在计佣订单「按合同汇总」页为每份合同配置链路成员每单金额):
-- crm_customer_pricing 增加 contract_id,非空即"按合同分配版本",计佣时优先于客户定价;
-- 历史客户定价行 contract_id 保持 NULL,latest(customerId) 查询已按 IS NULL 过滤,互不影响。
-- 纯增量变更(加列+索引),不改已有数据;丁超岗位调整请走后台「伙伴管理 → 调岗」。
ALTER TABLE crm_customer_pricing
    ADD COLUMN contract_id BIGINT NULL COMMENT '服务合同 crm_service_contract.id;非空=按合同分配版本,计佣优先于客户定价' AFTER customer_id,
    MODIFY COLUMN beneficiaries_json JSON NOT NULL COMMENT '其他受益人及每单金额快照(客户定价最多2名;合同分配为链路成员)';

CREATE INDEX idx_customer_pricing_contract ON crm_customer_pricing (contract_id);
