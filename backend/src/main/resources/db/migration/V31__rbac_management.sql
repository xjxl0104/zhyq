-- =====================================================================
-- V31 ver4.2 RBAC 管理闭环
-- 1) 清理并禁止重复的用户角色/角色权限关联
-- 2) 增加用户分配角色、角色配置权限两个管理权限点
-- 3) 自动授予受保护的 admin 角色
-- =====================================================================

DELETE a FROM sys_user_role a
JOIN sys_user_role b
  ON a.user_id = b.user_id AND a.role_id = b.role_id AND a.id > b.id;

DELETE a FROM sys_role_menu a
JOIN sys_role_menu b
  ON a.role_id = b.role_id AND a.menu_id = b.menu_id AND a.id > b.id;

ALTER TABLE sys_user_role
    ADD UNIQUE KEY uk_user_role (user_id, role_id);

ALTER TABLE sys_role_menu
    ADD UNIQUE KEY uk_role_menu (role_id, menu_id);

INSERT INTO sys_menu
    (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, '用户-分配角色', 3, 'system:user:role', 0, 0, 1, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perm = 'system:user:role' AND deleted = 0
);

INSERT INTO sys_menu
    (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, '角色-配置权限', 3, 'system:role:permission', 0, 0, 1, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perm = 'system:role:permission' AND deleted = 0
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.perm IN ('system:user:role', 'system:role:permission') AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
