ALTER TABLE crm_customer
    ADD COLUMN progress_percentage INT NOT NULL DEFAULT 0 COMMENT '服务对接进度百分比，签约为100';

CREATE TABLE crm_customer_progress_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    percentage INT NULL COMMENT '历史公开进度未记录百分比时为空，不能猜测补填',
    summary VARCHAR(500) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    create_time DATETIME NOT NULL,
    KEY idx_customer_progress (customer_id, tenant_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='伙伴可见服务进度的追加历史';

START TRANSACTION;
UPDATE crm_customer
SET progress_percentage = CASE WHEN status = 2 THEN 100
    WHEN warehouse_assignment_status = 2 THEN 60
    WHEN warehouse_assignment_status = 1 THEN 30 ELSE 0 END
WHERE deleted = 0;

-- 旧系统只保存过明确“向伙伴公开”的进度文本。严格限定动作，不能导入内部备注、原因或操作人。
INSERT INTO crm_customer_progress_history(customer_id, tenant_id, percentage, summary, stage, source, create_time)
SELECT a.biz_id, a.tenant_id, NULL, TRIM(a.after_json), 'historical', 'legacy', a.create_time
FROM sys_audit a
JOIN crm_customer c ON c.id = a.biz_id AND c.tenant_id = a.tenant_id AND c.deleted = 0
WHERE a.module = 'crm.marketing' AND a.action = 'customer.progress.publish' AND a.biz_type = 'customer'
  AND a.deleted = 0 AND a.create_time IS NOT NULL
  AND CHAR_LENGTH(TRIM(a.after_json)) BETWEEN 1 AND 500
ORDER BY a.create_time, a.id;

-- 审计可能因旧版“尽力保存”机制而缺失：补入当前仍可证实的公开快照，不编造旧进度。
INSERT INTO crm_customer_progress_history(customer_id, tenant_id, percentage, summary, stage, source, create_time)
SELECT c.id, c.tenant_id, NULL, TRIM(c.public_progress), 'historical', 'legacy-snapshot',
       COALESCE(c.progress_updated_at, NOW())
FROM crm_customer c
WHERE c.deleted = 0 AND CHAR_LENGTH(TRIM(c.public_progress)) BETWEEN 1 AND 500
  AND NOT EXISTS (SELECT 1 FROM crm_customer_progress_history h
                  WHERE h.customer_id = c.id AND h.tenant_id = c.tenant_id AND h.summary = TRIM(c.public_progress)
                    AND h.create_time = COALESCE(c.progress_updated_at, NOW()));

-- 本次启用百分比时的已知阶段快照，与旧的文本历史明确区分。
INSERT INTO crm_customer_progress_history(customer_id, tenant_id, percentage, summary, stage, source, create_time)
SELECT id, tenant_id, progress_percentage,
       CASE WHEN status = 2 THEN '进度启用时状态：已签约生效'
            WHEN warehouse_assignment_status = 2 THEN '进度启用时状态：云仓已承接'
            WHEN warehouse_assignment_status = 1 THEN '进度启用时状态：已分派，等待云仓承接'
            ELSE '进度启用时状态：等待服务对接' END,
       'snapshot', 'migration', NOW()
FROM crm_customer WHERE deleted = 0;
COMMIT;
