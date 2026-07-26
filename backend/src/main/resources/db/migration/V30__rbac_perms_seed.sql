-- =====================================================================
-- V30 RBAC 落地(ver4.0 · 3.4):敏感模块权限点 + admin 全量授权
-- 背景:此前无 sys_role_menu / sys_user_role 种子,sys_menu.perm 为空,
--       登录后所有人 authorities 为空。挂 @PreAuthorize 后需保证 admin 仍可全通。
-- 三件套(均幂等,可重复执行不产生重复行):
--   1) 插入权限点到 sys_menu(type=3 按钮,parent_id=0)
--   2) admin 角色关联全部权限点(sys_role_menu)
--   3) admin 用户关联 admin 角色(sys_user_role)
-- 命名:模块:资源:操作。与后端 @PreAuthorize 严格一致(docs-rbac-perms.md)。
-- =====================================================================

-- ---------- 1) 权限点插入(幂等:perm 已存在则跳过) ----------
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, t.nm, 3, t.p, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    -- 合同
    SELECT 'contract:query' AS p, '合同-查询' AS nm
    UNION ALL SELECT 'contract:add','合同-新增'
    UNION ALL SELECT 'contract:edit','合同-修改'
    UNION ALL SELECT 'contract:delete','合同-删除'
    UNION ALL SELECT 'contract:submit','合同-提交'
    UNION ALL SELECT 'contract:approve','合同-审批'
    UNION ALL SELECT 'contract:terminate','合同-终止'
    UNION ALL SELECT 'contract:archive','合同-归档'
    -- 财务:账单
    UNION ALL SELECT 'finance:bill:query','账单-查询'
    UNION ALL SELECT 'finance:bill:calcLateFee','账单-计算滞纳金'
    UNION ALL SELECT 'finance:bill:add','账单-新增'
    UNION ALL SELECT 'finance:bill:edit','账单-修改'
    UNION ALL SELECT 'finance:bill:delete','账单-删除'
    -- 财务:收款
    UNION ALL SELECT 'finance:payment:pay','收款-收款'
    UNION ALL SELECT 'finance:payment:query','收款-查询'
    -- 财务:发票
    UNION ALL SELECT 'finance:invoice:query','发票-查询'
    UNION ALL SELECT 'finance:invoice:add','发票-新增'
    UNION ALL SELECT 'finance:invoice:edit','发票-修改'
    UNION ALL SELECT 'finance:invoice:delete','发票-删除'
    -- 财务:收据
    UNION ALL SELECT 'finance:receipt:query','收据-查询'
    UNION ALL SELECT 'finance:receipt:add','收据-新增'
    UNION ALL SELECT 'finance:receipt:edit','收据-修改'
    UNION ALL SELECT 'finance:receipt:delete','收据-删除'
    UNION ALL SELECT 'finance:receipt:print','收据-打印'
    -- 财务:催缴通知
    UNION ALL SELECT 'finance:notice:query','催缴-查询'
    UNION ALL SELECT 'finance:notice:delete','催缴-删除'
    UNION ALL SELECT 'finance:notice:generate','催缴-生成'
    UNION ALL SELECT 'finance:notice:send','催缴-发送'
    -- 财务:流水
    UNION ALL SELECT 'finance:flow:query','流水-查询'
    -- 财务:设置
    UNION ALL SELECT 'finance:setting:query','财务设置-查询'
    UNION ALL SELECT 'finance:setting:edit','财务设置-修改'
    UNION ALL SELECT 'finance:setting:add','财务设置-新增'
    UNION ALL SELECT 'finance:setting:delete','财务设置-删除'
    -- 财务:报表
    UNION ALL SELECT 'finance:report:query','财务报表-查询'
    -- 系统:用户
    UNION ALL SELECT 'system:user:query','用户-查询'
    UNION ALL SELECT 'system:user:add','用户-新增'
    UNION ALL SELECT 'system:user:edit','用户-修改'
    UNION ALL SELECT 'system:user:delete','用户-删除'
    -- 系统:角色
    UNION ALL SELECT 'system:role:query','角色-查询'
    UNION ALL SELECT 'system:role:add','角色-新增'
    UNION ALL SELECT 'system:role:edit','角色-修改'
    UNION ALL SELECT 'system:role:delete','角色-删除'
    -- 系统:菜单
    UNION ALL SELECT 'system:menu:query','菜单-查询'
    UNION ALL SELECT 'system:menu:add','菜单-新增'
    UNION ALL SELECT 'system:menu:edit','菜单-修改'
    UNION ALL SELECT 'system:menu:delete','菜单-删除'
    -- 系统:资源
    UNION ALL SELECT 'system:resource:query','资源-查询'
    UNION ALL SELECT 'system:resource:add','资源-新增'
    UNION ALL SELECT 'system:resource:edit','资源-修改'
    UNION ALL SELECT 'system:resource:delete','资源-删除'
    -- 系统:部门
    UNION ALL SELECT 'system:dept:query','部门-查询'
    UNION ALL SELECT 'system:dept:add','部门-新增'
    UNION ALL SELECT 'system:dept:edit','部门-修改'
    UNION ALL SELECT 'system:dept:delete','部门-删除'
    -- 系统:岗位
    UNION ALL SELECT 'system:post:query','岗位-查询'
    UNION ALL SELECT 'system:post:add','岗位-新增'
    UNION ALL SELECT 'system:post:edit','岗位-修改'
    UNION ALL SELECT 'system:post:delete','岗位-删除'
    -- 系统:字典
    UNION ALL SELECT 'system:dict:query','字典-查询'
    UNION ALL SELECT 'system:dict:add','字典-新增'
    UNION ALL SELECT 'system:dict:edit','字典-修改'
    UNION ALL SELECT 'system:dict:delete','字典-删除'
    -- 系统:消息中心
    UNION ALL SELECT 'system:message:query','消息-查询'
    UNION ALL SELECT 'system:message:add','消息-新增'
    UNION ALL SELECT 'system:message:edit','消息-修改'
    UNION ALL SELECT 'system:message:delete','消息-删除'
    UNION ALL SELECT 'system:message:send','消息-发送'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm = t.p AND m.deleted = 0);

-- ---------- 2) admin 角色关联全部权限点(幂等) ----------
-- admin 角色 code='admin';把所有 type=3 权限点授予它,已关联则跳过。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.type = 3 AND m.perm IS NOT NULL AND m.perm <> '' AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- ---------- 3) admin 用户关联 admin 角色(幂等) ----------
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'admin' AND r.deleted = 0
WHERE u.username = 'admin' AND u.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

