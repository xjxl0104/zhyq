-- 伙伴提现审批流:伙伴在小程序提交(或每月 10 日系统自动提交)的提现申请进入审批链,
-- 默认一个节点「数智云仓财务审批」,审批人为角色「全民营销-财务」(mkt_finance),超级管理员也可办理。
-- 节点与审批人可在「预算管理 → 审批流程」页增删改;停用本定义后提现回到「提现审核」页单步审核。
INSERT INTO wf_definition (biz_type, name, status, tenant_id, create_by, create_time, version, deleted)
SELECT 'withdrawal', '伙伴提现审批流', 1, 1, 'system', NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_definition d WHERE d.biz_type = 'withdrawal' AND d.deleted = 0);

INSERT INTO wf_node (definition_id, seq, name, approver_type, approver_value, tenant_id, create_by, create_time, version, deleted)
SELECT d.id, 1, '数智云仓财务审批', 'role', 'mkt_finance', 1, 'system', NOW(), 1, 0
FROM (SELECT id FROM wf_definition WHERE biz_type = 'withdrawal' AND deleted = 0 ORDER BY id DESC LIMIT 1) d
WHERE NOT EXISTS (SELECT 1 FROM wf_node w WHERE w.definition_id = d.id AND w.deleted = 0);

-- 每月自动提现日(0 = 关闭)。提现申请默认全月可提交,不再限 25–31 日。
INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted)
SELECT 'marketing', 'auto_withdraw_day', '10', '每月自动提现日：当天 10:00 把伙伴全部可提现余额自动提交提现申请（0 关闭）', 1, 'system', NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM biz_setting WHERE module = 'marketing' AND skey = 'auto_withdraw_day' AND deleted = 0);
