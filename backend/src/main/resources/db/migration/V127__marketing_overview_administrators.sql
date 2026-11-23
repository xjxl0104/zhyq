-- User-authorized mini-program administration is limited to the two existing named accounts.
-- Stable identity IDs are granted once from verified phone records; requests cannot grant by phone.
UPDATE crm_marketing_account_control
SET super_admin=0,revision=revision+1,update_by='overview-v127',update_time=NOW()
WHERE identity_type='mp' AND super_admin=1
  AND identity_id IN (SELECT id FROM crm_promoter WHERE tenant_id=1)
  AND identity_id NOT IN (SELECT id FROM crm_promoter WHERE tenant_id=1 AND deleted=0 AND status=1 AND phone IN ('18824642574','13610238888'))
  AND (SELECT COUNT(*) FROM crm_promoter WHERE tenant_id=1 AND deleted=0 AND status=1 AND phone IN ('18824642574','13610238888'))=2;

INSERT INTO crm_marketing_account_control(identity_type,identity_id,super_admin,login_disabled,revision,update_by)
SELECT 'mp',id,1,0,1,'overview-v127' FROM crm_promoter
WHERE tenant_id=1 AND deleted=0 AND status=1 AND phone IN ('18824642574','13610238888')
  AND (SELECT COUNT(*) FROM (SELECT id FROM crm_promoter WHERE tenant_id=1 AND deleted=0 AND status=1 AND phone IN ('18824642574','13610238888')) candidates)=2
ON DUPLICATE KEY UPDATE super_admin=1,revision=revision+1,update_by='overview-v127',update_time=NOW();
