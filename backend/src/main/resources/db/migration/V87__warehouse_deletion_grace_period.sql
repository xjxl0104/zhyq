ALTER TABLE crm_warehouse
    ADD COLUMN delete_requested_at DATETIME NULL,
    ADD COLUMN delete_due_at DATETIME NULL,
    ADD COLUMN delete_reason VARCHAR(500) NULL,
    ADD COLUMN delete_previous_status TINYINT NULL,
    ADD KEY idx_warehouse_delete_due (deleted, join_status, delete_due_at);

-- 8 为删除冻结期。正式删除沿用 deleted 逻辑删除，保留合同、结算及维护记录。
ALTER TABLE crm_warehouse MODIFY join_status TINYINT NOT NULL DEFAULT 1
    COMMENT '1申请 2资质审核 3ERP对接中 4待签协议 5已上线 6暂停 7退出 8待删除冻结';

-- 缺少旧新增审计的云仓补录原始创建时间，明确区分补录与新操作。
INSERT INTO sys_audit(module,action,biz_type,biz_id,reason,after_json,operator,project_id,tenant_id,create_time)
SELECT 'crm.marketing','warehouse.apply','warehouse',w.id,'历史新增记录补录',
    JSON_OBJECT('id',w.id,'name',w.name,'code',w.code),COALESCE(w.create_by,'历史数据'),w.project_id,w.tenant_id,COALESCE(w.create_time,NOW())
FROM crm_warehouse w
WHERE NOT EXISTS (SELECT 1 FROM sys_audit a WHERE a.module='crm.marketing' AND a.biz_type='warehouse' AND a.biz_id=w.id AND a.action='warehouse.apply');
