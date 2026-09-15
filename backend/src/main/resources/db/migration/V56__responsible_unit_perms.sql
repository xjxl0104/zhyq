-- V56__responsible_unit_perms.sql 补齐「责任单位」权限点
--
-- 背景:ResponsibleUnitController 的查询/保存/导入/删除要求 property:unit:query / save / delete,
-- 但 V39 建表时没有登记这些权限点,sys_menu 里查不到 → 连超级管理员也「权限不足」。
-- 本迁移只新增权限点并授给 admin 角色,不改动任何已有数据;沿用 V52 的幂等写法。

INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, t.nm, 3, t.p, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'property:unit:query' AS p, '责任单位-查询' AS nm
    UNION ALL SELECT 'property:unit:save', '责任单位-新增/修改/导入'
    UNION ALL SELECT 'property:unit:delete', '责任单位-删除'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm = t.p AND m.deleted = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.perm IN ('property:unit:query', 'property:unit:save', 'property:unit:delete') AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
