-- V42__budget_management.sql 预算管理板块(基线 ver6.6)
--
-- 版本号说明:ver6.6 已占用 V41(V41__pur_workflow_perms_seed.sql,采购与审批链权限点),
-- 故本迁移排在 V42。
--
-- 板块调整:V40 建的「采购管理」升级为「预算管理」,与「办公」同级挂在「园区运营」下。
-- 预算管理含四块:年度预算 / 月度预算 / 年度采购计划 / 月度采购计划。
--   · 年度预算、月度预算 → 本迁移新建 bud_budget,附件走 sys_file(bizType='budget'),
--     提交后走 #18 审批链引擎(wf_definition.biz_type='budget'),审批人可在「审批流程」页配置。
--   · 年度/月度采购计划 → 沿用 V40 的 pur_plan;计划下的采购申请沿用 pur_request(bizType='procurement')。
-- 财务边界:amount 仅登记预算金额,不入账 / 不触发收款 / 不写 finance,与 V40 采购一致。

-- ==================== 预算(年度/月度) ====================
CREATE TABLE bud_budget (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    budget_no     VARCHAR(64)   NOT NULL COMMENT '预算编号(唯一)',
    title         VARCHAR(128)  NOT NULL COMMENT '预算名称',
    budget_type   TINYINT       NOT NULL COMMENT '1年度预算 2月度预算',
    period        VARCHAR(16)   NOT NULL COMMENT '周期,如 2026(年度) / 2026-03(月度)',
    department    VARCHAR(64)   NULL COMMENT '申请部门',
    applicant     VARCHAR(64)   NULL COMMENT '申请人',
    amount        DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '预算金额(仅登记,不入账)',
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1草稿 2审批中 3已通过 4已驳回 5已归档 6已取消',
    approver      VARCHAR(64)   NULL COMMENT '最近一次审批人(冗余展示,来自审批链回调)',
    approve_time  DATETIME      NULL COMMENT '最近一次审批时间',
    -- 逐节点审批意见存于 wf_task.opinion,详情页「审批轨迹」按节点展示,此处不冗余
    remark        VARCHAR(255)  NULL COMMENT '备注',
    tenant_id     BIGINT        NULL,
    create_by     VARCHAR(64)   NULL,
    create_time   DATETIME      NULL,
    update_by     VARCHAR(64)   NULL,
    update_time   DATETIME      NULL,
    version       INT           NOT NULL DEFAULT 0,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_budget_no (budget_no),
    KEY idx_type_period (budget_type, period),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算(年度/月度)';

-- ==================== 路由-模块映射 ====================
-- V40 把 /pur/** 记作「采购管理」,现整体归入「预算管理」模块口径
UPDATE route_module_mapping SET module = 'budget', module_name = '预算管理' WHERE route = '/pur/**';

INSERT INTO route_module_mapping (route, module, module_name, is_core, enabled)
SELECT '/budget/**', 'budget', '预算管理', 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM route_module_mapping r WHERE r.route = '/budget/**');

-- ==================== 预算审批流(样例 3 节点) ====================
-- 复用 #18 审批链引擎。节点与审批人可在前端「预算管理 → 审批流程」页增删改并调序。
-- 停用本定义后,预算提交将不建审批链(降级),不影响其余功能。
INSERT INTO wf_definition (biz_type, name, status, tenant_id, create_by, create_time, version, deleted)
SELECT 'budget', '预算申请审批流', 1, 1, 'system', NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_definition d WHERE d.biz_type = 'budget' AND d.deleted = 0);

-- 节点只在该定义还没有任何节点时铺一次,避免重复执行时把审批链插成 6 步
INSERT INTO wf_node (definition_id, seq, name, approver_type, approver_value, tenant_id, create_by, create_time, version, deleted)
SELECT d.id, n.seq, n.nm, 'role', n.av, 1, 'system', NOW(), 1, 0
FROM (SELECT id FROM wf_definition WHERE biz_type='budget' AND deleted=0 ORDER BY id DESC LIMIT 1) d
CROSS JOIN (
    SELECT 1 AS seq, '部门负责人审批' AS nm, 'dept_manager' AS av
    UNION ALL SELECT 2, '财务负责人审批', 'finance_director'
    UNION ALL SELECT 3, '总经理审批', 'gm'
) n
WHERE NOT EXISTS (SELECT 1 FROM wf_node w WHERE w.definition_id = d.id AND w.deleted = 0);

-- V40 的采购审批流改名,与板块口径一致(仍是 biz_type='procurement')
UPDATE wf_definition SET name = '采购申请审批流' WHERE biz_type = 'procurement' AND name = '采购审批流' AND deleted = 0;

-- ==================== 菜单(RBAC 可分配) ====================
-- 与 layout/menu.js 保持镜像:预算管理为一级目录,下挂四个业务菜单 + 审批流程。
-- 不写死 id(V30~V33 的权限点插入已推高 auto_increment),按名字回查父节点。
INSERT INTO sys_menu (parent_id, name, type, path, icon, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, '预算管理', 1, NULL, 'Wallet', 15, 1, 1, 1, 'system', NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.name = '预算管理' AND m.parent_id = 0 AND m.deleted = 0);

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT (SELECT id FROM (SELECT id FROM sys_menu WHERE name='预算管理' AND parent_id=0 AND deleted=0 ORDER BY id DESC LIMIT 1) p),
       s.nm, 2, s.pt, NULL, s.st, 1, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT '年度预算' AS nm, '/budget/annual' AS pt, 1 AS st
    UNION ALL SELECT '月度预算', '/budget/monthly', 2
    UNION ALL SELECT '年度采购计划', '/budget/plan-year', 3
    UNION ALL SELECT '月度采购计划', '/budget/plan-month', 4
    UNION ALL SELECT '审批流程', '/budget/flow', 5
) s
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.path = s.pt AND m.deleted = 0);

-- ==================== 权限点种子 ====================
-- 与 BudgetController 的 @PreAuthorize 严格一致。沿用 V30/V41 的幂等三件套:
--   1) 插权限点到 sys_menu(type=3 按钮,parent_id=0)
--   2) admin 角色关联全部权限点  3) admin 用户关联 admin 角色
-- 提交/归档/取消各自独立设点:能改草稿不等于能推进审批状态。
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, t.nm, 3, t.p, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'budget:query' AS p, '预算-查询' AS nm
    UNION ALL SELECT 'budget:add','预算-新增'
    UNION ALL SELECT 'budget:edit','预算-修改'
    UNION ALL SELECT 'budget:delete','预算-删除'
    UNION ALL SELECT 'budget:submit','预算-提交申请'
    UNION ALL SELECT 'budget:archive','预算-归档'
    UNION ALL SELECT 'budget:cancel','预算-取消'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.perm = t.p AND m.deleted = 0
);

-- admin 角色关联全部权限点(与 V30/V41 同一段查询,故新增点会一并授予 admin)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.type = 3 AND m.perm IS NOT NULL AND m.perm <> '' AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'admin' AND r.deleted = 0
WHERE u.username = 'admin' AND u.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- ==================== 种子数据 ====================
INSERT INTO bud_budget (budget_no, title, budget_type, period, department, applicant, amount, status, approver, approve_time, remark, create_by, create_time, version, deleted) VALUES
('BG-2026-001', '2026年度园区运营总预算', 1, '2026', '综合管理部', '王芳', 1860000.00, 3, 'admin', '2026-01-05 10:20:00', '含物业、能耗、行政三大口径', 'system', NOW(), 0, 0),
('BG-2026-002', '2026年度物业维保专项预算', 1, '2026', '物业部', '李强', 420000.00, 1, NULL, NULL, '设备维保与备品备件', 'system', NOW(), 0, 0),
('BG-202603-001', '2026年3月月度预算', 2, '2026-03', '综合管理部', '王芳', 155000.00, 3, 'admin', '2026-03-01 09:00:00', '按年度预算 1/12 拆分并微调', 'system', NOW(), 0, 0),
('BG-202604-001', '2026年4月月度预算', 2, '2026-04', '物业部', '张伟', 38000.00, 1, NULL, NULL, '待提交审批', 'system', NOW(), 0, 0);
