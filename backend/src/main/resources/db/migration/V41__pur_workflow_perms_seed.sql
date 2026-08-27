-- =====================================================================
-- V41 采购模块 + 审批链配置 权限点种子(ver6.6)
-- 背景:V40 建了采购表并挂了 /pur/** 路由映射, 但没有任何权限点;
--       PR #4 的 18 个新接口全部无 @PreAuthorize, 任何登录用户皆可调。
--       本迁移配合 ver6.6 的 @PreAuthorize 补全, 补上对应权限位。
-- 沿用 V30 的幂等三件套写法:
--   1) 插入权限点到 sys_menu(type=3 按钮, parent_id=0)
--   2) admin 角色关联全部权限点(sys_role_menu)
--   3) admin 用户关联 admin 角色(sys_user_role)
-- 命名:模块:资源:操作。与后端 @PreAuthorize 严格一致(docs/upgrade/rbac-perms-3.4.md)。
--
-- 注:workflow:definition:manage 覆盖流程定义/节点的读写共 6 个接口。
--     审批链定义是合同/采购等多 bizType 共用的"谁能审批"配置, 能改即可自审,
--     故按管理级权限单独设点, 不与采购权限混用。
--     运行时接口(start/approve/reject/task/instance)本次不动:
--     它们是审批人日常操作, 收口需另定角色口径, 属存量问题。
-- =====================================================================

-- ---------- 1) 权限点插入(幂等:perm 已存在则跳过) ----------
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, t.nm, 3, t.p, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    -- 采购:计划
    SELECT 'pur:plan:query' AS p, '采购计划-查询' AS nm
    UNION ALL SELECT 'pur:plan:add','采购计划-新增'
    UNION ALL SELECT 'pur:plan:edit','采购计划-修改'
    UNION ALL SELECT 'pur:plan:delete','采购计划-删除'
    -- 采购:申请
    UNION ALL SELECT 'pur:request:query','采购申请-查询'
    UNION ALL SELECT 'pur:request:add','采购申请-新增'
    UNION ALL SELECT 'pur:request:edit','采购申请-修改'
    UNION ALL SELECT 'pur:request:delete','采购申请-删除'
    UNION ALL SELECT 'pur:request:submit','采购申请-提交审批'
    UNION ALL SELECT 'pur:request:complete','采购申请-标记完成'
    UNION ALL SELECT 'pur:request:cancel','采购申请-取消'
    -- 审批链:流程定义配置
    UNION ALL SELECT 'workflow:definition:manage','审批链定义-配置'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.perm = t.p AND m.deleted = 0
);

-- ---------- 2) admin 角色关联全部权限点(幂等) ----------
-- 与 V30 同一段查询:抓所有 type=3 权限点, 因此本次新增的点会一并授予 admin。
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
