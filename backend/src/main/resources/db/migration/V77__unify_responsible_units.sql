-- Preserve the old table and link every historical unit to one supplier.
-- Match an existing supplier only for a unique, global legacy unit. Project
-- specific units stay distinct so their service scope and ownership survive.
UPDATE pm_responsible_unit u
JOIN (SELECT tenant_id, name, MIN(id) AS supplier_id
      FROM pur_supplier WHERE deleted = 0 GROUP BY tenant_id, name
      HAVING COUNT(*) = 1) s
  ON s.tenant_id = u.tenant_id AND s.name = u.name
JOIN (SELECT tenant_id, name, COUNT(*) AS total
      FROM pm_responsible_unit GROUP BY tenant_id, name) n
  ON n.tenant_id = u.tenant_id AND n.name = u.name
SET u.supplier_id = s.supplier_id
WHERE u.supplier_id IS NULL AND u.project_id IS NULL
  AND n.total = 1;

UPDATE pur_supplier s
JOIN pm_responsible_unit u ON u.supplier_id = s.id
SET s.contact = COALESCE(NULLIF(s.contact, ''), u.contact),
    s.phone = COALESCE(NULLIF(s.phone, ''), u.contact_phone),
    s.service_scope = COALESCE(NULLIF(s.service_scope, ''), u.service_scope),
    s.business_scope = COALESCE(NULLIF(s.business_scope, ''), u.service_scope),
    s.unit_type = COALESCE(NULLIF(s.unit_type, ''), u.unit_type),
    s.remark = COALESCE(NULLIF(s.remark, ''), u.remark);

INSERT INTO pur_supplier
    (code, name, category, contact, phone, business_scope, unit_type,
     service_scope, project_id, status, remark, tenant_id, create_by,
     create_time, update_by, update_time, version, deleted)
SELECT CONCAT('LEGACY-GYS-', u.id), u.name, 'property', u.contact,
       u.contact_phone, u.service_scope, u.unit_type, u.service_scope,
       u.project_id, IF(u.deleted = 1, 3, IF(u.enabled = 1, 1, 2)), u.remark,
       u.tenant_id, u.create_by, u.create_time, u.update_by, u.update_time,
       u.version, 0
FROM pm_responsible_unit u
WHERE u.supplier_id IS NULL;

UPDATE pm_responsible_unit u
JOIN pur_supplier s ON s.code = CONCAT('LEGACY-GYS-', u.id)
SET u.supplier_id = s.id
WHERE u.supplier_id IS NULL;

UPDATE pm_work_order w
JOIN pm_responsible_unit u ON u.id = w.responsible_unit_id AND u.tenant_id = w.tenant_id
SET w.supplier_id = u.supplier_id
WHERE w.supplier_id IS NULL;
