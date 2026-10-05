-- Carry existing responsibility-unit grants into the unified supplier module.
-- Contract permissions remain separately assignable.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT old_grant.role_id, target.id
FROM sys_role_menu old_grant
JOIN sys_menu old_menu ON old_menu.id = old_grant.menu_id
JOIN sys_menu target ON target.perm = CASE
    WHEN old_menu.perm = 'property:unit:query' THEN 'pur:supplier:query'
    WHEN old_menu.perm = 'property:unit:delete' THEN 'pur:supplier:delete'
    ELSE 'pur:supplier:add' END AND target.deleted = 0
WHERE old_menu.perm IN ('property:unit:query', 'property:unit:save', 'property:unit:delete')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing
                  WHERE existing.role_id = old_grant.role_id AND existing.menu_id = target.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT old_grant.role_id, target.id
FROM sys_role_menu old_grant
JOIN sys_menu old_menu ON old_menu.id = old_grant.menu_id
JOIN sys_menu target ON target.perm IN ('pur:supplier:edit', 'pur:supplier:status') AND target.deleted = 0
WHERE old_menu.perm = 'property:unit:save'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing
                  WHERE existing.role_id = old_grant.role_id AND existing.menu_id = target.id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT old_grant.user_id, target.id
FROM sys_user_menu old_grant
JOIN sys_menu old_menu ON old_menu.id = old_grant.menu_id
JOIN sys_menu target ON target.perm = CASE
    WHEN old_menu.perm = 'property:unit:query' THEN 'pur:supplier:query'
    WHEN old_menu.perm = 'property:unit:delete' THEN 'pur:supplier:delete'
    ELSE 'pur:supplier:add' END AND target.deleted = 0
WHERE old_menu.perm IN ('property:unit:query', 'property:unit:save', 'property:unit:delete')
  AND NOT EXISTS (SELECT 1 FROM sys_user_menu existing
                  WHERE existing.user_id = old_grant.user_id AND existing.menu_id = target.id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT old_grant.user_id, target.id
FROM sys_user_menu old_grant
JOIN sys_menu old_menu ON old_menu.id = old_grant.menu_id
JOIN sys_menu target ON target.perm IN ('pur:supplier:edit', 'pur:supplier:status') AND target.deleted = 0
WHERE old_menu.perm = 'property:unit:save'
  AND NOT EXISTS (SELECT 1 FROM sys_user_menu existing
                  WHERE existing.user_id = old_grant.user_id AND existing.menu_id = target.id);

-- The old write API can no longer create a second, diverging supplier record.
UPDATE sys_menu SET status = 0
WHERE perm IN ('property:unit:query', 'property:unit:save', 'property:unit:delete')
  AND deleted = 0;
