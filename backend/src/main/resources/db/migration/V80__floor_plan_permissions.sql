-- Floor-plan permissions follow explicit building-page grants, never all users.
INSERT INTO sys_menu (parent_id,name,type,perm,sort,visible,status,tenant_id,create_by,create_time,version,deleted)
SELECT COALESCE((SELECT MIN(id) FROM sys_menu WHERE path='/building/building' AND deleted=0),0),
       p.label,3,p.perm,0,0,1,1,'system',NOW(),1,0
FROM (SELECT 'building:floorPlan:query' perm, '楼层平面图-查看' label
      UNION ALL SELECT 'building:floorPlan:edit', '楼层平面图-上传') p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm=p.perm AND m.deleted=0);

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.perm IN ('building:floorPlan:query','building:floorPlan:edit') AND m.deleted=0
WHERE r.deleted=0 AND (r.code='admin' OR EXISTS (
    SELECT 1 FROM sys_role_menu g JOIN sys_menu p ON p.id=g.menu_id
    WHERE g.role_id=r.id AND p.path='/building/building' AND p.deleted=0 AND p.status=1
)) AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id=r.id AND e.menu_id=m.id);

INSERT INTO sys_user_menu(user_id,menu_id)
SELECT DISTINCT g.user_id,m.id FROM sys_user_menu g
JOIN sys_menu p ON p.id=g.menu_id AND p.path='/building/building' AND p.deleted=0 AND p.status=1
JOIN sys_menu m ON m.perm IN ('building:floorPlan:query','building:floorPlan:edit') AND m.deleted=0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_menu e WHERE e.user_id=g.user_id AND e.menu_id=m.id);
