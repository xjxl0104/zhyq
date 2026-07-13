-- =====================================================================
-- V5 财务:账单、明细、支付、流水、发票
-- 金额统一 DECIMAL(14,2)。收款幂等靠 pay_no 唯一约束。
-- =====================================================================

-- 账单
CREATE TABLE fin_bill (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(64)  NOT NULL COMMENT '账单号(全局唯一)',
    contract_id    BIGINT       COMMENT '来源合同',
    tenant_ref_id  BIGINT       COMMENT '租客',
    project_id     BIGINT,
    building_id    BIGINT,
    room_id        BIGINT,
    -- 方向:1收款 2付款
    direction      TINYINT      NOT NULL DEFAULT 1 COMMENT '1收款 2付款',
    fee_type       VARCHAR(32)  COMMENT '费用类型:租金/物业费/保证金/能源费/服务费/一次性',
    -- 来源:合同计划/抄表/人工/商城/预约/工单/接口
    source         VARCHAR(32)  DEFAULT '合同计划',
    -- 状态:1草稿 2待审核 3待收付 4部分结清 5已结清 6逾期 7退款中 8作废
    status         TINYINT      NOT NULL DEFAULT 3 COMMENT '账单状态(附录B)',
    amount         DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '应收/应付金额',
    paid_amount    DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '实收金额',
    late_fee       DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '滞纳金',
    tax_rate       DECIMAL(6,2)  DEFAULT 0 COMMENT '税率%',
    period_start   DATE         COMMENT '账期起',
    period_end     DATE         COMMENT '账期止',
    due_date       DATE         COMMENT '应收日',
    overdue_days   INT          DEFAULT 0 COMMENT '逾期天数',
    invoice_status TINYINT      DEFAULT 0 COMMENT '0未开 1已开',
    remark         VARCHAR(255),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_bill_code (code, deleted),
    KEY idx_bill_status (status), KEY idx_bill_contract (contract_id), KEY idx_due (due_date)
) COMMENT '账单';

-- 支付单/收款单
CREATE TABLE fin_payment (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    pay_no         VARCHAR(64)  NOT NULL COMMENT '支付流水号(幂等键)',
    bill_id        BIGINT       NOT NULL,
    amount         DECIMAL(14,2) NOT NULL,
    pay_method     VARCHAR(32)  COMMENT '现金/转账/POS/微信/支付宝/聚合',
    pay_time       DATETIME,
    operator       VARCHAR(32),
    remark         VARCHAR(255),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_pay_no (pay_no),
    KEY idx_pay_bill (bill_id)
) COMMENT '支付/收款单';

-- 收支流水
CREATE TABLE fin_flow (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    flow_no        VARCHAR(64)  NOT NULL,
    direction      TINYINT      NOT NULL COMMENT '1收 2支',
    amount         DECIMAL(14,2) NOT NULL,
    bill_id        BIGINT,
    payment_id     BIGINT,
    match_status   TINYINT      DEFAULT 1 COMMENT '0未匹配 1已匹配',
    flow_time      DATETIME,
    remark         VARCHAR(255),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '收支流水';

-- 发票
CREATE TABLE fin_invoice (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(64),
    bill_id        BIGINT,
    tenant_ref_id  BIGINT,
    title          VARCHAR(128) COMMENT '发票抬头',
    tax_no         VARCHAR(64)  COMMENT '税号',
    amount         DECIMAL(14,2) NOT NULL DEFAULT 0,
    invoice_type   VARCHAR(32)  COMMENT '普票/专票',
    -- 状态:1申请 2审核 3已开 4红冲 5作废
    status         TINYINT      NOT NULL DEFAULT 1,
    remark         VARCHAR(255),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '发票';

-- 调账单(调增/调减/减免/坏账)
CREATE TABLE fin_adjust (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_id        BIGINT       NOT NULL,
    adjust_type    VARCHAR(32)  COMMENT '调增/调减/减免/坏账/退款',
    before_amount  DECIMAL(14,2),
    after_amount   DECIMAL(14,2),
    reason         VARCHAR(255),
    approval_id    BIGINT       COMMENT '关联审批',
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '调账单';
