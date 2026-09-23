-- Keep signed historical snapshots and existing financial rows unchanged.
ALTER TABLE crm_service_contract ADD COLUMN terms_effective_from DATE NULL COMMENT '当前条款版本生效日，空表示合同起始日';
