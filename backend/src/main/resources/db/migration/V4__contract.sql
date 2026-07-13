-- =====================================================================
-- V4 合同全生命周期 + 计费条款 + 账单计划
-- =====================================================================

-- 合同
CREATE TABLE biz_contract (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(64)  NOT NULL COMMENT '合同编号(全局唯一)',
    tenant_ref_id  BIGINT       NOT NULL COMMENT '租客',
    project_id     BIGINT       NOT NULL,
    -- 类型:1正式 2意向 3草稿 4电子 5优惠 6成本
    contract_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '合同类型',
    -- 状态:1草稿 2待审核 3待签署 4待执行 5执行中 6变更中 7退租中 8已到期 9已终止 10已归档
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '合同状态(附录B)',
    start_date     DATE         COMMENT '起始日期',
    end_date       DATE         COMMENT '结束日期',
    sign_date      DATE         COMMENT '签订日期',
    rent_price     DECIMAL(12,2) DEFAULT 0 COMMENT '租赁单价(元/㎡/月)',
    property_price DECIMAL(12,2) DEFAULT 0 COMMENT '物业单价',
    rent_area      DECIMAL(12,2) DEFAULT 0 COMMENT '租赁面积',
    deposit        DECIMAL(14,2) DEFAULT 0 COMMENT '保证金',
    -- 计费方式:1按面积单价 2固定金额 3阶梯单价
    charge_mode    TINYINT      NOT NULL DEFAULT 1 COMMENT '计租方式',
    pay_cycle      TINYINT      NOT NULL DEFAULT 3 COMMENT '付款周期(月):1/3/6/12',
    free_months    INT          DEFAULT 0 COMMENT '免租月数',
    increase_rate  DECIMAL(6,2) DEFAULT 0 COMMENT '年递增率%',
    source         VARCHAR(32)  COMMENT '来源',
    terminate_date DATE         COMMENT '退租时间',
    remark         VARCHAR(500),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_contract_code (code, deleted),
    KEY idx_contract_status (status), KEY idx_contract_tenant (tenant_ref_id)
) COMMENT '合同';

-- 合同-房源关系(N:M)
CREATE TABLE biz_contract_room (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id    BIGINT       NOT NULL,
    room_id        BIGINT       NOT NULL,
    rent_area      DECIMAL(12,2) DEFAULT 0,
    rent_price     DECIMAL(12,2) DEFAULT 0,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_c (contract_id), KEY idx_r (room_id)
) COMMENT '合同房源关系';

-- 合同版本(变更/续租/退租留痕)
CREATE TABLE biz_contract_version (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id    BIGINT       NOT NULL,
    version_no     INT          NOT NULL COMMENT '版本序号',
    change_type    VARCHAR(32)  COMMENT '变更类型:续租/扩租/缩租/换房/变更单价/变更账期/主体变更/退租',
    snapshot       TEXT         COMMENT '变更前快照(JSON)',
    effect_date    DATE         COMMENT '生效日期',
    remark         VARCHAR(500),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_cv (contract_id)
) COMMENT '合同版本';

-- 审批实例(通用)
CREATE TABLE biz_approval (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type       VARCHAR(32)  NOT NULL COMMENT '业务类型:contract/refund/adjust/terminate',
    biz_id         BIGINT       NOT NULL COMMENT '业务单据ID',
    title          VARCHAR(255),
    -- 状态:1草稿 2审批中 3已通过 4已驳回 5已撤回 6已终止
    status         TINYINT      NOT NULL DEFAULT 2 COMMENT '审批状态(附录B)',
    apply_by       VARCHAR(32),
    approve_by     VARCHAR(32),
    approve_time   DATETIME,
    opinion        VARCHAR(500) COMMENT '审批意见',
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_biz (biz_type, biz_id)
) COMMENT '审批实例';
