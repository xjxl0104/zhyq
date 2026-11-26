-- =====================================================================
-- V138:
--   1. 合同模块菜单整合:
--      - 「合同列表」(/contract/list)更名「正式合同」,合并原「合同归档」能力
--      - 下线独立「合同归档」(/contract/archive)菜单项(保留数据与附件)
--      - 同级新增「简易合同」(/contract/simple)与「合同模板」(/contract/template)
--   2. 新建简易合同表 ct_simple_contract(按楼层 + 自编号房号 X01/X02 管理注册公司用合同)
--   3. 新建合同模板表 ct_contract_template(租赁合同/物业合同/简易合同/补充协议等模板库)
-- =====================================================================

-- ---------- 1. 合同菜单更新与授权 ----------
UPDATE sys_menu SET name = '正式合同', sort = 1
WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'contract/list';

UPDATE sys_menu SET visible = 0, status = 0
WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'contract/archive';

SET @contract_parent = COALESCE(
    (SELECT id FROM sys_menu WHERE deleted = 0 AND type = 1 AND name = '合同' AND (path IS NULL OR path = '') ORDER BY id LIMIT 1),
    (SELECT parent_id FROM sys_menu WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'contract/list' ORDER BY id LIMIT 1),
    0
);

CREATE TEMPORARY TABLE contract_new_pages (path VARCHAR(64) NOT NULL, name VARCHAR(32) NOT NULL, sort INT NOT NULL);
INSERT INTO contract_new_pages (path, name, sort) VALUES
('/contract/simple', '简易合同', 2),
('/contract/template', '合同模板', 3);

INSERT INTO sys_menu (parent_id, name, type, path, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT @contract_parent, p.name, 2, p.path, p.sort, 1, 1, 1, 'system', NOW(), 1, 0
FROM contract_new_pages p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE TRIM(BOTH '/' FROM m.path) = TRIM(BOTH '/' FROM p.path) AND m.deleted = 0
);

-- 原有合同权限的角色与用户自动获得「简易合同」「合同模板」页面权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT g.role_id, page.id
FROM sys_role_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('contract/list', 'contract/archive', 'contract/setting')
JOIN contract_new_pages p
JOIN sys_menu page ON TRIM(BOTH '/' FROM page.path) = TRIM(BOTH '/' FROM p.path) AND page.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu e WHERE e.role_id = g.role_id AND e.menu_id = page.id
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT r.id, page.id
FROM sys_role r
JOIN contract_new_pages p
JOIN sys_menu page ON TRIM(BOTH '/' FROM page.path) = TRIM(BOTH '/' FROM p.path) AND page.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = r.id AND e.menu_id = page.id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT g.user_id, page.id
FROM sys_user_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('contract/list', 'contract/archive', 'contract/setting')
JOIN contract_new_pages p
JOIN sys_menu page ON TRIM(BOTH '/' FROM page.path) = TRIM(BOTH '/' FROM p.path) AND page.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_menu e WHERE e.user_id = g.user_id AND e.menu_id = page.id
);
DROP TEMPORARY TABLE contract_new_pages;

-- ---------- 2. 简易合同表(注册公司用,按楼层 + 自编号房号 X01 / X02) ----------
CREATE TABLE ct_simple_contract (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    project_id     BIGINT        NULL COMMENT '园区ID',
    floor_no       INT           NOT NULL DEFAULT 1 COMMENT '楼层号(1,2,3...)',
    room_code      VARCHAR(32)   NOT NULL COMMENT '自编号房号(如 101, 102, 201, 202)',
    contract_no    VARCHAR(64)   NULL COMMENT '简易合同编号',
    company_name   VARCHAR(128)  NOT NULL COMMENT '注册公司名称',
    credit_code    VARCHAR(64)   NULL COMMENT '统一社会信用代码',
    contact_name   VARCHAR(64)   NULL COMMENT '法人/联系人',
    contact_phone  VARCHAR(32)   NULL COMMENT '联系电话',
    reg_address    VARCHAR(255)  NULL COMMENT '注册地址',
    start_date     DATE          NULL COMMENT '合同起始日',
    end_date       DATE          NULL COMMENT '合同到期日',
    annual_fee     DECIMAL(14,2) NULL DEFAULT 0 COMMENT '费用(元)',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态:1生效中 2已到期 3已注销',
    remark         VARCHAR(500)  NULL,
    tenant_id      BIGINT        NOT NULL DEFAULT 1,
    create_by      VARCHAR(32)   NULL,
    create_time    DATETIME      NULL,
    update_by      VARCHAR(32)   NULL,
    update_time    DATETIME      NULL,
    version        INT           NOT NULL DEFAULT 1,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_simple_contract_floor (floor_no, room_code),
    KEY idx_simple_contract_company (company_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '简易合同台账(公司注册用)';

-- ---------- 3. 合同模板表(租赁合同/物业合同/简易合同/补充协议等) ----------
CREATE TABLE ct_contract_template (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(128) NOT NULL COMMENT '模板名称',
    category         VARCHAR(32)  NOT NULL COMMENT '模板分类:租赁合同/物业合同/简易合同/补充协议/退租协议/其他模板',
    version_tag      VARCHAR(32)  NULL DEFAULT 'V1.0' COMMENT '版本号',
    applicable_scope VARCHAR(255) NULL COMMENT '适用说明/场景',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    sort             INT          NOT NULL DEFAULT 10,
    remark           VARCHAR(500) NULL,
    tenant_id        BIGINT       NOT NULL DEFAULT 1,
    create_by        VARCHAR(32)  NULL,
    create_time      DATETIME     NULL,
    update_by        VARCHAR(32)  NULL,
    update_time      DATETIME     NULL,
    version          INT          NOT NULL DEFAULT 1,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_contract_tpl_category (category, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '园区合同模板库';
