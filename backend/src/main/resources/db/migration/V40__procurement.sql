-- V40__procurement.sql 采购管理域（新增板块）
-- 采购计划(年度/月度/临时) -> 采购申请(明细+附件) -> 走审批链工作流(#18 wf_definition/wf_node,复用现成引擎)。
-- 财务边界：total_amount/budget_amount 仅记录预算/实付金额，不入账/不触发收款/不写 finance。
-- 附件：直接用现成 sys_file 通用附件表(bizType='pur_request'),不建新表。

-- 采购计划(年度/月度/临时)
CREATE TABLE pur_plan (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    plan_no         VARCHAR(64)   NOT NULL COMMENT '计划编号(唯一)',
    title           VARCHAR(128)  NOT NULL COMMENT '计划名称',
    plan_type       TINYINT       NOT NULL COMMENT '1年度 2月度 3临时',
    period          VARCHAR(16)   NOT NULL COMMENT '周期,如 2026(年度) / 2026-03(月度) / 临时计划可留空或填申请日期',
    department      VARCHAR(64)   NULL COMMENT '申请部门',
    applicant       VARCHAR(64)   NULL COMMENT '制定人',
    budget_amount   DECIMAL(12,2) NULL COMMENT '预算金额(仅记录,不入账)',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1草稿 2生效 3已完成 4已关闭',
    remark          VARCHAR(255)  NULL COMMENT '备注',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_no (plan_no),
    KEY idx_type_period (plan_type, period),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购计划';

-- 采购申请(可关联某采购计划;走审批链工作流)
CREATE TABLE pur_request (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    request_no      VARCHAR(64)   NOT NULL COMMENT '采购申请单号(唯一)',
    plan_id         BIGINT        NULL COMMENT '关联采购计划(可空,支持计划外临时申请)',
    title           VARCHAR(128)  NOT NULL COMMENT '采购事由/标题',
    supplier        VARCHAR(128)  NULL COMMENT '供应商名称',
    applicant       VARCHAR(64)   NULL COMMENT '申请人',
    department      VARCHAR(64)   NULL COMMENT '申请部门',
    space_id        BIGINT        NULL COMMENT '关联空间(挂 #3 sys_space 树,可选)',
    total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '采购总金额(=明细金额汇总)',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1草稿 2审批中 3已通过 4已驳回 5已完成 6已取消',
    approver        VARCHAR(64)   NULL COMMENT '最近一次审批人(冗余展示,来自审批链回调)',
    approve_time    DATETIME      NULL COMMENT '最近一次审批时间',
    -- 审批意见不在此冗余:逐节点意见存于 wf_task.opinion,详情页「审批轨迹」按节点展示完整意见
    remark          VARCHAR(255)  NULL COMMENT '备注',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_plan (plan_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购申请';

CREATE TABLE pur_request_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    request_id      BIGINT        NOT NULL COMMENT '采购申请ID',
    item_name       VARCHAR(128)  NOT NULL COMMENT '物品名称',
    spec            VARCHAR(128)  NULL COMMENT '规格型号',
    unit            VARCHAR(16)   NULL COMMENT '单位',
    qty             DECIMAL(12,2) NOT NULL DEFAULT 1 COMMENT '数量',
    unit_price      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '单价',
    amount          DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '金额(=qty*unit_price)',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购申请明细';

-- 路由-模块映射(供模块开关/RBAC 使用)
INSERT INTO route_module_mapping (route, module, module_name, is_core, enabled) VALUES
('/pur/**', 'pur', '采购管理', 0, 1);

-- 样例:采购审批流(2 节点),复用 #18 现成审批链引擎(wf_definition/wf_node)。
-- 停用此定义即可让 workflowService.start() 降级(不建审批链,由前端直接标记审批中,零代码回滚)。
INSERT INTO wf_definition (biz_type, name, status, tenant_id, create_by, create_time, version, deleted) VALUES
 ('procurement', '采购审批流', 1, 1, 'system', NOW(), 1, 0);

INSERT INTO wf_node (definition_id, seq, name, approver_type, approver_value, tenant_id, create_by, create_time, version, deleted) VALUES
 ((SELECT id FROM (SELECT id FROM wf_definition WHERE biz_type='procurement' AND deleted=0 ORDER BY id DESC LIMIT 1) t), 1, '部门负责人审批', 'role', 'dept_manager', 1, 'system', NOW(), 1, 0),
 ((SELECT id FROM (SELECT id FROM wf_definition WHERE biz_type='procurement' AND deleted=0 ORDER BY id DESC LIMIT 1) t), 2, '采购负责人审批', 'role', 'pur_manager', 1, 'system', NOW(), 1, 0);

-- 种子数据
INSERT INTO pur_plan (plan_no, title, plan_type, period, department, applicant, budget_amount, status, remark, create_by, create_time, version, deleted) VALUES
('PP-2026-001', '2026年度办公物资采购计划', 1, '2026', '行政部', '王芳', 50000.00, 2, '年度常规办公耗材与设备预算', 'system', NOW(), 0, 0),
('PP-2026-03', '2026年3月物业维护采购计划', 2, '2026-03', '物业部', '李强', 15000.00, 2, '月度设备维护物料', 'system', NOW(), 0, 0);

INSERT INTO pur_request (request_no, plan_id, title, supplier, applicant, department, total_amount, status, approver, approve_time, remark, create_by, create_time, version, deleted) VALUES
('PR-20260101-001', (SELECT id FROM (SELECT id FROM pur_plan WHERE plan_no='PP-2026-001' LIMIT 1) t1), '办公耗材采购', '晨光文具', '王芳', '行政部', 1280.00, 5, 'admin', '2026-01-03 10:00:00', '季度常规补货', 'system', NOW(), 0, 0),
('PR-20260215-001', (SELECT id FROM (SELECT id FROM pur_plan WHERE plan_no='PP-2026-03' LIMIT 1) t2), '会议室投影设备采购', '爱普生代理商', '李强', '物业部', 8600.00, 3, 'admin', '2026-02-16 09:30:00', 'B座302会议室升级', 'system', NOW(), 0, 0),
('PR-20260310-001', NULL, '巡更对讲机采购(计划外)', '摩托罗拉授权店', '张伟', '物业部', 3600.00, 1, NULL, NULL, '待提交审批', 'system', NOW(), 0, 0);

INSERT INTO pur_request_item (request_id, item_name, spec, unit, qty, unit_price, amount, create_by, create_time, version, deleted)
SELECT id, 'A4打印纸', '70g/500张', '箱', 20, 32.00, 640.00, 'system', NOW(), 0, 0 FROM pur_request WHERE request_no = 'PR-20260101-001'
UNION ALL
SELECT id, '中性笔', '0.5mm黑色', '盒', 32, 20.00, 640.00, 'system', NOW(), 0, 0 FROM pur_request WHERE request_no = 'PR-20260101-001'
UNION ALL
SELECT id, '爱普生投影仪', 'EB-2250U', '台', 2, 4300.00, 8600.00, 'system', NOW(), 0, 0 FROM pur_request WHERE request_no = 'PR-20260215-001'
UNION ALL
SELECT id, '数字对讲机', 'GP328D', '台', 6, 600.00, 3600.00, 'system', NOW(), 0, 0 FROM pur_request WHERE request_no = 'PR-20260310-001';
