-- 财务总览沿用 finance:report:query。这里只登记页面与继承原报表页面授权，
-- 不按角色名称批量授权，也不新增或扩大 API 查询权限。
INSERT INTO sys_menu
    (parent_id, name, type, path, component, icon, sort, visible, status,
     tenant_id, create_by, create_time, version, deleted)
SELECT COALESCE((SELECT parent_id FROM sys_menu
                 WHERE TRIM(BOTH '/' FROM path) = 'finance/report' AND deleted = 0
                 ORDER BY id LIMIT 1), 0),
       '财务总览', 2, '/finance/overview', 'finance/Overview', 'Money',
       COALESCE((SELECT MIN(sort) - 1 FROM sys_menu
                 WHERE TRIM(BOTH '/' FROM path) LIKE 'finance/%'
                   AND type = 2 AND deleted = 0), 0),
       1, 1, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu
                  WHERE TRIM(BOTH '/' FROM path) = 'finance/overview' AND deleted = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT old_grant.role_id, overview.id
FROM sys_role_menu old_grant
JOIN sys_menu report ON report.id = old_grant.menu_id
    AND TRIM(BOTH '/' FROM report.path) = 'finance/report'
    AND report.type <> 3 AND report.status = 1 AND report.deleted = 0
JOIN sys_menu overview ON TRIM(BOTH '/' FROM overview.path) = 'finance/overview'
    AND overview.status = 1 AND overview.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu existing
                  WHERE existing.role_id = old_grant.role_id AND existing.menu_id = overview.id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT old_grant.user_id, overview.id
FROM sys_user_menu old_grant
JOIN sys_menu report ON report.id = old_grant.menu_id
    AND TRIM(BOTH '/' FROM report.path) = 'finance/report'
    AND report.type <> 3 AND report.status = 1 AND report.deleted = 0
JOIN sys_menu overview ON TRIM(BOTH '/' FROM overview.path) = 'finance/overview'
    AND overview.status = 1 AND overview.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_menu existing
                  WHERE existing.user_id = old_grant.user_id AND existing.menu_id = overview.id);
