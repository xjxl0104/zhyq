-- Copy existing contacts into the independent directory without changing biz_tenant.
INSERT INTO pur_tenant_contact
    (tenant_name, project_id, tenant_ref_id, contact, phone, tenant_id,
     create_by, create_time, update_by, update_time, version, deleted)
SELECT t.name, t.project_id, t.id, TRIM(t.contact), t.phone, t.tenant_id,
       t.create_by, COALESCE(t.create_time, NOW()), t.update_by, t.update_time, 1, 0
FROM biz_tenant t
WHERE t.deleted = 0 AND t.contact IS NOT NULL AND TRIM(t.contact) <> '';
