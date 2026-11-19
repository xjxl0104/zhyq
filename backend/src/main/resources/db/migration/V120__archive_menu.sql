-- V120 资料管理菜单与权限点:挂在「办公」目录同级(园区运营下),仅授权 admin 角色,其他角色由管理员在角色管理里勾选。
INSERT INTO sys_menu (parent_id, name, type, path, component, icon, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT COALESCE((SELECT parent_id FROM sys_menu WHERE name = '办公' AND type = 1 AND deleted = 0 ORDER BY id LIMIT 1), 0),
       '资料管理', 1, NULL, NULL, 'FolderOpened', 10, 1, 1, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE name = '资料管理' AND type = 1 AND deleted = 0);
SET @arc_parent = (SELECT id FROM sys_menu WHERE name = '资料管理' AND type = 1 AND deleted = 0 ORDER BY id LIMIT 1);

INSERT INTO sys_menu (parent_id, name, type, path, component, icon, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT @arc_parent, '资料库', 2, '/archive/doc', 'archive/Doc', NULL, 1, 1, 1, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/archive/doc' AND deleted = 0);
INSERT INTO sys_menu (parent_id, name, type, path, component, icon, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT @arc_parent, '分类与规则', 2, '/archive/setting', 'archive/Setting', NULL, 2, 1, 1, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/archive/setting' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT COALESCE((SELECT id FROM sys_menu WHERE path = '/archive/doc' AND deleted = 0 ORDER BY id LIMIT 1), 0),
       p.label, 3, p.perm, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (SELECT 'archive:doc:query' perm, '资料-查询' label
      UNION ALL SELECT 'archive:doc:add', '资料-上传'
      UNION ALL SELECT 'archive:doc:edit', '资料-编辑') p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm = p.perm AND m.deleted = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND (m.name = '资料管理' AND m.type = 1
        OR m.path IN ('/archive/doc', '/archive/setting') OR m.perm LIKE 'archive:doc:%')
WHERE r.deleted = 0 AND r.code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id);
