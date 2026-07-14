-- 批次② 轻量自建审批链工作流引擎(设计文档 §3)
-- 4 张新表:流程定义 wf_definition / 节点定义 wf_node / 审批实例 wf_instance / 节点任务 wf_task
-- D1-方案A:保留 biz_approval 作单据头,wf_instance.approval_id 挂其上;删 wf_* 不影响合同主数据。
-- D2:无登录鉴权,审批人(assignee/approver_value)暂按"角色码/用户名约定值"占位,真实指派待 #7 鉴权轮。

-- 流程定义
CREATE TABLE IF NOT EXISTS wf_definition (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type    VARCHAR(32)  NOT NULL COMMENT '绑定业务:contract/contract_change/contract_terminate/fee_waiver',
    name        VARCHAR(64)  NOT NULL COMMENT '流程名,如"合同审批流"',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32)           DEFAULT NULL,
    create_time DATETIME              DEFAULT NULL,
    update_by   VARCHAR(32)           DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    version     INT          NOT NULL DEFAULT 1,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_wf_def_biz (biz_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流-流程定义';

-- 节点定义(属于某 definition)
CREATE TABLE IF NOT EXISTS wf_node (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    definition_id  BIGINT      NOT NULL COMMENT '所属流程定义',
    seq            INT         NOT NULL COMMENT '节点顺序(1,2,3…)',
    name           VARCHAR(64) NOT NULL COMMENT '节点名,如"部门经理审批"',
    approver_type  VARCHAR(16)          DEFAULT 'role' COMMENT 'role/user/dept(本期先支持 role/user)',
    approver_value VARCHAR(64)          DEFAULT NULL COMMENT '审批人标识(角色码/用户名),占位待 #7',
    tenant_id      BIGINT      NOT NULL DEFAULT 1,
    create_by      VARCHAR(32)          DEFAULT NULL,
    create_time    DATETIME             DEFAULT NULL,
    update_by      VARCHAR(32)          DEFAULT NULL,
    update_time    DATETIME             DEFAULT NULL,
    version        INT         NOT NULL DEFAULT 1,
    deleted        TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_wf_node_def (definition_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流-节点定义';

-- 审批实例(一次审批一条)
CREATE TABLE IF NOT EXISTS wf_instance (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    definition_id BIGINT      NOT NULL COMMENT '用哪个流程定义',
    biz_type      VARCHAR(32) NOT NULL COMMENT '关联业务类型,如 contract',
    biz_id        BIGINT      NOT NULL COMMENT '关联业务单据ID,如合同id',
    approval_id   BIGINT               DEFAULT NULL COMMENT '关联 biz_approval 单据头(D1-方案A)',
    current_seq   INT         NOT NULL DEFAULT 1 COMMENT '当前到第几个节点',
    status        TINYINT     NOT NULL DEFAULT 1 COMMENT '1审批中 2通过 3驳回 4撤回',
    tenant_id     BIGINT      NOT NULL DEFAULT 1,
    create_by     VARCHAR(32)          DEFAULT NULL,
    create_time   DATETIME             DEFAULT NULL,
    update_by     VARCHAR(32)          DEFAULT NULL,
    update_time   DATETIME             DEFAULT NULL,
    version       INT         NOT NULL DEFAULT 1,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_wf_inst_biz (biz_type, biz_id),
    KEY idx_wf_inst_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流-审批实例';

-- 节点任务(每到一个节点生成一条)
CREATE TABLE IF NOT EXISTS wf_task (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    instance_id BIGINT       NOT NULL COMMENT '所属实例',
    node_id     BIGINT       NOT NULL COMMENT '对应节点定义',
    seq         INT          NOT NULL COMMENT '节点顺序',
    assignee    VARCHAR(64)           DEFAULT NULL COMMENT '审批人(占位待 #7 接真实用户)',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1待审 2通过 3驳回',
    opinion     VARCHAR(512)          DEFAULT NULL COMMENT '审批意见',
    act_time    DATETIME              DEFAULT NULL COMMENT '处理时间',
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32)           DEFAULT NULL,
    create_time DATETIME              DEFAULT NULL,
    update_by   VARCHAR(32)           DEFAULT NULL,
    update_time DATETIME              DEFAULT NULL,
    version     INT          NOT NULL DEFAULT 1,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_wf_task_inst (instance_id, seq),
    KEY idx_wf_task_assignee (assignee, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流-节点任务';

-- 样例:合同审批流(2 节点),方便联调。停用此定义即可让 start() 降级回旧单节点审批(零代码回滚)。
INSERT INTO wf_definition (biz_type, name, status, tenant_id, create_by, create_time, version, deleted) VALUES
 ('contract', '合同审批流', 1, 1, 'system', NOW(), 1, 0);

INSERT INTO wf_node (definition_id, seq, name, approver_type, approver_value, tenant_id, create_by, create_time, version, deleted) VALUES
 ((SELECT id FROM (SELECT id FROM wf_definition WHERE biz_type='contract' AND deleted=0 ORDER BY id DESC LIMIT 1) t), 1, '部门经理审批', 'role', 'dept_manager', 1, 'system', NOW(), 1, 0),
 ((SELECT id FROM (SELECT id FROM wf_definition WHERE biz_type='contract' AND deleted=0 ORDER BY id DESC LIMIT 1) t), 2, '财务总监审批', 'role', 'finance_director', 1, 'system', NOW(), 1, 0);
