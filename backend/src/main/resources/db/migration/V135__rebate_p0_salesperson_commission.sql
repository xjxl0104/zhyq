-- 1) 客户分派返点（平台服务费）确认与云仓承接锁定
ALTER TABLE crm_customer
    ADD COLUMN rebate_policy_type VARCHAR(32) NULL COMMENT '分派返点政策: BULK_5000(日均5000单及以上0.1元/单) / STANDARD(日均5000单以下0.15元/单) / STRATEGIC(战略客户一客一议)',
    ADD COLUMN rebate_unit_price DECIMAL(14,3) NULL COMMENT '分派确认的返点单价(元/单)，商家确认承接后锁定为总计佣单价',
    ADD COLUMN rebate_confirmed_at DATETIME NULL COMMENT '云仓商家确认承接并确认返点的时间';

-- 2) 服务合同快照云仓已确认返点单价（作为合同分佣总单价锁定依据）
ALTER TABLE crm_service_contract
    ADD COLUMN rebate_policy_type VARCHAR(32) NULL COMMENT '合同快照的云仓确认返点政策',
    ADD COLUMN rebate_unit_price DECIMAL(14,3) NULL COMMENT '合同快照的云仓确认返点单价(元/单)';

-- 3) 新增 P0 角色（云仓业务员）
INSERT INTO crm_position (code, name, sort, share_pct, share_min_pct, lock_cap, promote_amount, promote_orders, team_counted, demote_enabled, status, tenant_id, create_by, create_time, version, deleted)
SELECT 'P0', '云仓业务员', 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM crm_position p WHERE p.code = 'P0' AND p.deleted = 0);

-- 4) 云仓业务员联系人关联 P0 伙伴档案
ALTER TABLE crm_warehouse_contact
    ADD COLUMN promoter_id BIGINT NULL COMMENT '关联的全民营销伙伴ID(P0云仓业务员)',
    ADD INDEX idx_wh_contact_promoter (promoter_id);

-- 将已存在且手机号相同的云仓业务员关联到伙伴档案（不改已有 P1-P4 岗位）
UPDATE crm_warehouse_contact c
JOIN crm_promoter p ON p.phone = c.phone AND p.deleted = 0
SET c.promoter_id = p.id
WHERE c.deleted = 0 AND c.role = 'member' AND c.promoter_id IS NULL
  AND c.phone IS NOT NULL AND c.phone <> '';

-- 5) 客户/合同分佣版本及计佣订单记录业务员(P0)固定佣金快照(0.02元/单)
ALTER TABLE crm_customer_pricing
    ADD COLUMN salesperson_per_order DECIMAL(14,3) NULL COMMENT '业务员(P0)固定每单佣金快照(0.020元/单)；历史旧版本为空';

ALTER TABLE crm_referral_order
    ADD COLUMN salesperson_commission_per_order DECIMAL(15,4) NULL COMMENT '业务员(P0)固定佣金每单快照',
    ADD COLUMN salesperson_contact_id BIGINT NULL COMMENT '计佣时负责该客户的云仓业务员联系人ID快照';

-- 6) 安全幂等补齐超级管理员“杨洪”的后台账号手机号（与 V97 丁超逻辑一致，仅唯一匹配且未被占用时补齐）
UPDATE sys_user SET phone = '13610238888'
WHERE deleted = 0 AND (nickname = '杨洪' OR nickname LIKE '杨洪(%' OR nickname LIKE '杨洪（%')
  AND (SELECT cnt FROM (SELECT COUNT(*) AS cnt FROM sys_user
       WHERE deleted = 0 AND (nickname = '杨洪' OR nickname LIKE '杨洪(%' OR nickname LIKE '杨洪（%')) t1) = 1
  AND (phone IS NULL OR phone = '' OR phone <> '13610238888')
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_user WHERE deleted = 0 AND phone = '13610238888') t2);
