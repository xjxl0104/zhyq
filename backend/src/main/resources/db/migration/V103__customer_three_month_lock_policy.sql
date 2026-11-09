-- 推荐客户直接锁定三个自然月，签约成交后归属长期有效。
-- 运营确认：现有未成交客户自本次迁移起重新锁定三个自然月。
-- 仅处理未删除且仍有当前在册推荐人的客户；保留所有旧锁定历史，不恢复已转移的旧归属。
-- 建议部署前备份 crm_customer_lock；本迁移本身不改客户推荐人或合同/佣金数据。
CREATE TEMPORARY TABLE tmp_customer_lock_policy_103 (
    customer_id BIGINT PRIMARY KEY,
    promoter_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL,
    has_deal TINYINT NOT NULL
);

START TRANSACTION;
SET @customer_lock_policy_at = NOW();

INSERT INTO tmp_customer_lock_policy_103 (customer_id, promoter_id, project_id, tenant_id, has_deal)
SELECT c.id, c.referrer_id, c.project_id, c.tenant_id,
       CASE WHEN EXISTS (
           SELECT 1 FROM crm_customer_lock l
           WHERE l.customer_id = c.id AND l.promoter_id = c.referrer_id
             AND l.tenant_id = c.tenant_id AND l.deleted = 0
             AND (l.status = 3 OR (l.status = 4 AND l.released_by = 'contract'))
       ) OR EXISTS (
           SELECT 1 FROM crm_service_contract sc
           WHERE sc.customer_id = c.id AND sc.partner_id = c.referrer_id
             AND sc.tenant_id = c.tenant_id AND sc.deleted = 0
             AND sc.status IN (4, 5, 6, 7, 8)
       ) OR c.status = 2 THEN 1 ELSE 0 END
FROM crm_customer c
JOIN crm_promoter p ON p.id = c.referrer_id AND p.tenant_id = c.tenant_id AND p.deleted = 0
WHERE c.deleted = 0 AND c.referrer_id IS NOT NULL;

-- 关闭旧活锁再新增当前归属的锁，避免旧 active_key 唯一键冲突；历史期限完整保留。
UPDATE crm_customer_lock l
JOIN tmp_customer_lock_policy_103 t ON t.customer_id = l.customer_id AND t.tenant_id = l.tenant_id
SET l.status = 4, l.released_reason = '锁定规则更新，当前归属按新记录执行',
    l.released_by = 'policy-migration', l.released_at = @customer_lock_policy_at,
    l.update_by = 'policy-migration', l.update_time = @customer_lock_policy_at, l.version = l.version + 1
WHERE l.deleted = 0 AND l.status IN (1, 2);

INSERT INTO crm_customer_lock (
    customer_id, promoter_id, status, prelock_until, lock_until, confirmed_by, confirmed_at,
    extended_count, project_id, tenant_id, create_by, create_time, update_by, update_time, version, deleted
)
SELECT customer_id, promoter_id, IF(has_deal = 1, 3, 2), NULL,
       IF(has_deal = 1, NULL, DATE_ADD(@customer_lock_policy_at, INTERVAL 3 MONTH)),
       'policy-migration', @customer_lock_policy_at, 0, project_id, tenant_id,
       'policy-migration', @customer_lock_policy_at, 'policy-migration', @customer_lock_policy_at, 1, 0
FROM tmp_customer_lock_policy_103;

COMMIT;
DROP TEMPORARY TABLE tmp_customer_lock_policy_103;
