-- =====================================================================
-- V58 全民营销 · 权限点 + 角色种子 + 路由映射
-- 沿用 V30/V41 的幂等三件套:
--   1) 权限点插入 sys_menu(type=3, parent_id=0),命名 crm:marketing:<资源>:<query|edit|audit|config|pay>
--   2) admin 角色关联全部新权限点
--   3) 新增三个业务角色(招商专员 / 运营 / 财务)并按 PARK-MKT-001 §6.4 权限矩阵关联
--   4) route_module_mapping 挂 /crm/marketing/**
-- 与后端 marketing/controller/* 的 @PreAuthorize 严格一一对应。
-- =====================================================================

-- ---------- 1) 权限点 ----------
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, t.nm, 3, t.p, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'crm:marketing:dashboard:query' AS p, '全民营销-看板-查询' AS nm
    -- 伙伴
    UNION ALL SELECT 'crm:marketing:promoter:query',   '全民营销-伙伴-查询'
    UNION ALL SELECT 'crm:marketing:promoter:edit',    '全民营销-伙伴-编辑(录入/改上级/调岗/冻结)'
    UNION ALL SELECT 'crm:marketing:promoter:audit',   '全民营销-伙伴-审核'
    -- 岗位 / 评级
    UNION ALL SELECT 'crm:marketing:position:query',   '全民营销-岗位份额-查询'
    UNION ALL SELECT 'crm:marketing:position:config',  '全民营销-岗位份额-配置'
    UNION ALL SELECT 'crm:marketing:grade:query',      '全民营销-客户评级-查询'
    UNION ALL SELECT 'crm:marketing:grade:config',     '全民营销-客户评级-配置'
    -- 客户
    UNION ALL SELECT 'crm:marketing:customer:query',   '全民营销-客户-查询'
    UNION ALL SELECT 'crm:marketing:customer:edit',    '全民营销-客户-编辑(评级/分配/锁定/流失)'
    -- 合同 / 模板
    UNION ALL SELECT 'crm:marketing:contract:query',   '全民营销-服务合同-查询'
    UNION ALL SELECT 'crm:marketing:contract:edit',    '全民营销-服务合同-起草/提交/签署/变更'
    UNION ALL SELECT 'crm:marketing:contract:audit',   '全民营销-服务合同-审核/终止/作废'
    UNION ALL SELECT 'crm:marketing:template:query',   '全民营销-合同模板-查询'
    UNION ALL SELECT 'crm:marketing:template:config',  '全民营销-合同模板-配置'
    -- 云仓 / 加盟
    UNION ALL SELECT 'crm:marketing:warehouse:query',  '全民营销-云仓-查询'
    UNION ALL SELECT 'crm:marketing:warehouse:edit',   '全民营销-云仓-编辑'
    UNION ALL SELECT 'crm:marketing:warehouse:audit',  '全民营销-云仓-暂停/恢复/退出'
    UNION ALL SELECT 'crm:marketing:onboarding:query', '全民营销-加盟申请-查询'
    UNION ALL SELECT 'crm:marketing:onboarding:audit', '全民营销-加盟申请-审核'
    -- 计佣订单 / 佣金 / 提现
    UNION ALL SELECT 'crm:marketing:order:query',      '全民营销-计佣订单-查询'
    UNION ALL SELECT 'crm:marketing:order:edit',       '全民营销-计佣订单-导入/作废/重算'
    UNION ALL SELECT 'crm:marketing:commission:query', '全民营销-佣金-查询'
    UNION ALL SELECT 'crm:marketing:commission:edit',  '全民营销-佣金-作废'
    UNION ALL SELECT 'crm:marketing:commission:pay',   '全民营销-佣金-批量结算'
    UNION ALL SELECT 'crm:marketing:withdrawal:query', '全民营销-提现-查询'
    UNION ALL SELECT 'crm:marketing:withdrawal:audit', '全民营销-提现-审核'
    UNION ALL SELECT 'crm:marketing:withdrawal:pay',   '全民营销-提现-标记打款'
    -- 设置 / 审计
    UNION ALL SELECT 'crm:marketing:setting:query',    '全民营销-规则参数-查询'
    UNION ALL SELECT 'crm:marketing:setting:config',   '全民营销-规则参数-配置'
    UNION ALL SELECT 'crm:marketing:audit:query',      '全民营销-审计日志-查询'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.perm = t.p AND m.deleted = 0
);

-- ---------- 2) admin 拿全部 ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.type = 3 AND m.perm LIKE 'crm:marketing:%' AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- ---------- 3) 三个业务角色 ----------
INSERT INTO sys_role (code, name, data_scope, sort, status, tenant_id, create_by, create_time, version, deleted)
SELECT t.code, t.name, 1, t.sort, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'mkt_sales' AS code, '全民营销-招商专员' AS name, 21 AS sort
    UNION ALL SELECT 'mkt_ops',     '全民营销-运营', 22
    UNION ALL SELECT 'mkt_finance', '全民营销-财务', 23
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role r WHERE r.code = t.code AND r.deleted = 0);

-- 角色 × 权限(PARK-MKT-001 §6.4)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM (
    -- 招商专员
    SELECT 'mkt_sales' AS role_code, 'crm:marketing:dashboard:query' AS perm
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:promoter:query'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:position:query'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:grade:query'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:customer:query'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:customer:edit'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:contract:query'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:contract:edit'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:template:query'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:warehouse:query'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:onboarding:query'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:order:query'
    UNION ALL SELECT 'mkt_sales', 'crm:marketing:commission:query'
    -- 运营
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:dashboard:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:promoter:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:promoter:edit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:promoter:audit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:position:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:grade:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:customer:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:customer:edit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:contract:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:contract:audit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:template:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:warehouse:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:warehouse:edit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:warehouse:audit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:onboarding:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:onboarding:audit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:order:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:order:edit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:commission:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:commission:edit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:withdrawal:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:withdrawal:audit'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:setting:query'
    UNION ALL SELECT 'mkt_ops', 'crm:marketing:audit:query'
    -- 财务
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:dashboard:query'
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:promoter:query'
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:contract:query'
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:warehouse:query'
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:order:query'
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:commission:query'
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:commission:pay'
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:withdrawal:query'
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:withdrawal:pay'
    UNION ALL SELECT 'mkt_finance', 'crm:marketing:audit:query'
) t
JOIN sys_role r ON r.code = t.role_code AND r.deleted = 0
JOIN sys_menu m ON m.perm = t.perm AND m.type = 3 AND m.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
);

-- ---------- 4) 路由映射(访问日志按模块归类) ----------
INSERT INTO route_module_mapping (route, module, module_name, is_core, enabled)
SELECT '/crm/marketing/**', 'crm', '招商管理', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM route_module_mapping WHERE route = '/crm/marketing/**');
