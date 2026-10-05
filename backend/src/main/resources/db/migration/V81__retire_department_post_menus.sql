-- Retire navigation and assignable permissions only. Historical departments,
-- posts, user associations and role data scopes remain intact.
UPDATE sys_menu SET visible=0, status=0
WHERE deleted=0 AND (
    TRIM(LEADING '/' FROM path) IN ('system/dept','system/post')
    OR perm LIKE 'system:dept:%' OR perm LIKE 'system:post:%'
);

UPDATE sys_menu SET sort=1 WHERE deleted=0 AND TRIM(LEADING '/' FROM path)='system/role';
UPDATE sys_menu SET sort=2 WHERE deleted=0 AND TRIM(LEADING '/' FROM path)='system/user';
