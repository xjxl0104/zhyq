-- A legacy referral may belong to the tenant without a project. Only resolve it
-- automatically when that tenant has exactly one live project and all known
-- customer relationships agree. Never overwrite an existing project.
UPDATE crm_customer c
JOIN crm_quote_intake q ON q.customer_id=c.id AND q.tenant_id=c.tenant_id
JOIN (SELECT tenant_id, MIN(id) AS project_id FROM biz_project WHERE deleted=0
      GROUP BY tenant_id HAVING COUNT(*)=1) s ON s.tenant_id=c.tenant_id
LEFT JOIN crm_promoter p ON p.id=c.referrer_id
SET c.project_id=s.project_id, c.version=COALESCE(c.version,0)+1
WHERE c.deleted=0 AND c.project_id IS NULL AND q.project_id IS NULL
  AND (p.id IS NULL OR (p.tenant_id=c.tenant_id AND (p.project_id IS NULL OR p.project_id=s.project_id)))
  AND NOT EXISTS(SELECT 1 FROM crm_warehouse w WHERE w.id IN (c.assigned_warehouse_id,c.intended_warehouse_id)
        AND (w.tenant_id<>c.tenant_id OR (w.project_id IS NOT NULL AND w.project_id<>s.project_id)));

UPDATE crm_quote_intake q
JOIN crm_customer c ON c.id=q.customer_id AND c.tenant_id=q.tenant_id AND c.deleted=0
JOIN biz_project p ON p.id=c.project_id AND p.tenant_id=c.tenant_id AND p.deleted=0
SET q.project_id=c.project_id
WHERE q.project_id IS NULL;

INSERT INTO crm_quote_advice(customer_id,tenant_id,project_id)
SELECT q.customer_id,q.tenant_id,q.project_id FROM crm_quote_intake q
JOIN crm_customer c ON c.id=q.customer_id AND c.tenant_id=q.tenant_id AND c.project_id=q.project_id AND c.deleted=0
JOIN biz_project p ON p.id=c.project_id AND p.tenant_id=c.tenant_id AND p.deleted=0
WHERE q.submitted_at IS NOT NULL
ON DUPLICATE KEY UPDATE customer_id=crm_quote_advice.customer_id;

UPDATE crm_quote_advice SET status='pending',generation=generation+1,suggestions=NULL,updated_at=NOW()
WHERE status='waiting';
