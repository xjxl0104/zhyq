-- =====================================================================
-- V53 供应商管理:供应商档案 + 供应商合同
--
-- 背景:系统此前只管"客户侧"(租客 biz_tenant + 租赁合同 biz_contract),
--      供应商侧完全空白 —— 采购申请里的供应商只是一个自由文本字段
--      (pur_request.supplier VARCHAR(128), V40:37), 没有任何主数据。
--      本迁移补上供应商侧的两张基础表。
--
-- 两个设计决定(2026-09-11 负责人拍板):
--   1) 不复用 biz_contract 存供应商合同 —— 它 tenant_ref_id NOT NULL(租客必填),
--      且字段是租赁语义(租金单价/面积/免租月/房源关联 biz_contract_room),
--      供应商合同是采购服务语义(合同金额/结算周期/账期), 混表会污染租赁合同。
--   2) 供应商类别走通用字典 sys_dict_type/sys_dict_data(类型 supplier_category),
--      负责人可在「系统管理→字典管理」自行增删类别, 不需要改代码上线 ——
--      这是本次的明确需求("有一个自己增加入口")。合同类型同理。
--
-- 本次不动采购模块:pur_request.supplier 保持自由文本原样(负责人拍板"先不动采购")。
-- 路由映射复用已登记的 '/pur/**' 行(V40 建、V42 已 UPDATE 改判为 module 'budget'/'预算管理'),
-- 供应商接口挂在 /pur/supplier*, 归入预算管理模块口径,与菜单位置自洽,故无需新增映射行。
-- 编号唯一键为单列 (code) 而非 (code, deleted):本表编号由服务端 max+1 生成,
-- 若唯一键带 deleted, 软删行会"释放"编号,下次生成重复号、再删即撞键。
-- 同仓所有自动生成编号的表(uk_plan_no/uk_request_no/uk_budget_no/uk_lead_no)皆为单列。
-- =====================================================================

-- ---------- 1) 供应商档案 ----------
CREATE TABLE pur_supplier (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(32)  NOT NULL COMMENT '供应商编号 GYS-0001(服务端生成)',
    name           VARCHAR(128) NOT NULL COMMENT '供应商名称',
    category       VARCHAR(64)  COMMENT '类别:字典 supplier_category 的 value',
    credit_code    VARCHAR(32)  COMMENT '统一社会信用代码',
    legal_person   VARCHAR(64)  COMMENT '法人',
    contact        VARCHAR(64)  COMMENT '联系人',
    phone          VARCHAR(20)  COMMENT '联系电话',
    email          VARCHAR(128) COMMENT '邮箱',
    reg_address    VARCHAR(255) COMMENT '注册地址',
    bank_name      VARCHAR(128) COMMENT '开户行',
    bank_account   VARCHAR(64)  COMMENT '银行账号',
    business_scope VARCHAR(512) COMMENT '经营范围',
    qualification  VARCHAR(512) COMMENT '资质说明',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2停用 3已归档',
    remark         VARCHAR(255),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_supplier_code (code),
    KEY idx_supplier_category (category),
    KEY idx_supplier_status (status),
    KEY idx_supplier_name (name)
) COMMENT '供应商档案';

-- ---------- 2) 供应商合同 ----------
CREATE TABLE pur_supplier_contract (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(32)   NOT NULL COMMENT '合同编号 GYSHT-2026-0001(服务端生成)',
    supplier_id   BIGINT        NOT NULL COMMENT '供应商(逻辑外键 pur_supplier.id,同项目惯例不建 FK)',
    name          VARCHAR(200)  NOT NULL COMMENT '合同名称,如 2026年度保洁服务合同',
    contract_type VARCHAR(64)   COMMENT '合同类型:字典 supplier_contract_type 的 value',
    amount        DECIMAL(14,2) COMMENT '合同金额(元)',
    start_date    DATE          COMMENT '生效日期',
    end_date      DATE          COMMENT '到期日期',
    sign_date     DATE          COMMENT '签订日期',
    pay_cycle     VARCHAR(32)   COMMENT '结算周期:月结/季结/一次性等',
    pay_terms     VARCHAR(255)  COMMENT '付款方式/账期说明',
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1草稿 2执行中 3已到期 4已终止',
    remark        VARCHAR(255),
    tenant_id     BIGINT        NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_supplier_contract_code (code),
    KEY idx_sc_supplier (supplier_id),
    KEY idx_sc_status (status),
    KEY idx_sc_end_date (end_date)
) COMMENT '供应商合同';

-- ---------- 3) 字典:类别与合同类型(负责人可自行增删) ----------
-- 幂等:已存在同名字典类型则跳过,便于重复执行/环境补种。
INSERT INTO sys_dict_type (dict_type, dict_name, status, tenant_id, create_by, create_time, version, deleted)
SELECT t.tp, t.nm, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'supplier_category' AS tp, '供应商类别' AS nm
    UNION ALL SELECT 'supplier_contract_type', '供应商合同类型'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type d WHERE d.dict_type = t.tp AND d.deleted = 0
);

INSERT INTO sys_dict_data (dict_type, label, value, color, sort, status, tenant_id, create_by, create_time, version, deleted)
SELECT t.tp, t.lb, t.val, t.co, t.so, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'supplier_category' AS tp, '物业服务' AS lb, 'property'     AS val, 'success' AS co, 1 AS so
    UNION ALL SELECT 'supplier_category', '工程装修', 'construction', 'warning', 2
    UNION ALL SELECT 'supplier_category', '办公采购', 'office',       'primary', 3
    UNION ALL SELECT 'supplier_contract_type', '服务合同', 'service',  'success', 1
    UNION ALL SELECT 'supplier_contract_type', '采购合同', 'purchase', 'primary', 2
    UNION ALL SELECT 'supplier_contract_type', '工程合同', 'project',  'warning', 3
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data d
     WHERE d.dict_type = t.tp AND d.value = t.val AND d.deleted = 0
);

-- ---------- 4) 权限点种子(沿用 V30/V41 幂等三件套) ----------
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, t.nm, 3, t.p, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    -- 供应商档案
    SELECT 'pur:supplier:query' AS p, '供应商档案-查询' AS nm
    UNION ALL SELECT 'pur:supplier:add','供应商档案-新增'
    UNION ALL SELECT 'pur:supplier:edit','供应商档案-修改'
    UNION ALL SELECT 'pur:supplier:delete','供应商档案-删除'
    UNION ALL SELECT 'pur:supplier:status','供应商档案-启停用/归档'
    -- 供应商合同
    UNION ALL SELECT 'pur:supplierContract:query','供应商合同-查询'
    UNION ALL SELECT 'pur:supplierContract:add','供应商合同-新增'
    UNION ALL SELECT 'pur:supplierContract:edit','供应商合同-修改'
    UNION ALL SELECT 'pur:supplierContract:delete','供应商合同-删除'
    UNION ALL SELECT 'pur:supplierContract:status','供应商合同-状态流转'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.perm = t.p AND m.deleted = 0
);

-- admin 角色关联全部权限点(与 V30/V41 同一段查询,新增点会一并授予)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.type = 3 AND m.perm IS NOT NULL AND m.perm <> '' AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- admin 用户关联 admin 角色(幂等)
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'admin' AND r.deleted = 0
WHERE u.username = 'admin' AND u.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
