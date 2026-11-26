-- V146:
-- 1. 清理 eng_lease_contract 中指向已删除正式合同的悬空 biz_contract_id
-- 2. 在招商租赁「正式合同」(biz_contract) 中补充「广州云帕供应链管理有限公司」的完整正式合同信息并关联
-- 3. 新增「缴费通知单抬头-按缴费单位定制配置」表 eng_notice_header_profile，支持通用/定制持久化

-- 1) 清理悬空关联
UPDATE eng_lease_contract lc
LEFT JOIN biz_contract bc ON bc.id = lc.biz_contract_id AND bc.deleted = 0
SET lc.biz_contract_id = NULL
WHERE lc.deleted = 0 AND lc.biz_contract_id IS NOT NULL AND bc.id IS NULL;

-- 2) 确保 biz_tenant 中存在「广州云帕供应链管理有限公司」
SET @default_project_id := COALESCE(
    (SELECT id FROM biz_project WHERE deleted = 0 ORDER BY id ASC LIMIT 1),
    1
);

INSERT INTO biz_tenant (code, name, tenant_type, project_id, status, tenant_id, create_by, create_time, version, deleted)
SELECT 'ZK-YUNPA-001', '广州云帕供应链管理有限公司', 1, @default_project_id, 1, 1, 'system', NOW(), 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM biz_tenant
    WHERE deleted = 0
      AND name COLLATE utf8mb4_general_ci = '广州云帕供应链管理有限公司'
);

SET @yunpa_tenant_id := (
    SELECT id FROM biz_tenant
    WHERE deleted = 0
      AND name COLLATE utf8mb4_general_ci = '广州云帕供应链管理有限公司'
    ORDER BY id ASC LIMIT 1
);

-- 确保 biz_contract 中存在「广州云帕供应链管理有限公司」的正式合同(优先取合同基础台账实时数据，缺省按台账标准条款兜底)
INSERT INTO biz_contract (
    code, tenant_ref_id, project_id, lease_scope, contract_type, status,
    start_date, end_date, rent_price, property_price, rent_area, deposit,
    charge_mode, pay_cycle, free_months, increase_rate, source, remark,
    tenant_id, create_by, create_time, version, deleted
)
SELECT
    COALESCE(NULLIF(TRIM(lc.agreement_no), ''), 'GZCJ-(DI PARK)-20260701-003 / WYFW-(DI PARK)-20260701-003'),
    @yunpa_tenant_id,
    COALESCE(lc.project_id, @default_project_id),
    COALESCE(NULLIF(TRIM(lc.unit_name), ''), 'DI PARK数智云仓产业园首层A区'),
    1, 5,
    COALESCE(lc.start_date, '2026-07-01'),
    COALESCE(lc.end_date, '2032-06-30'),
    ROUND(COALESCE(lc.rent_unit_price, 30.00), 2),
    ROUND(COALESCE(lc.property_unit_price, 2.00), 2),
    COALESCE(lc.lease_area, 3300.00),
    ROUND(COALESCE(lc.rent_deposit, 198000.00) + COALESCE(lc.property_deposit, 13200.00), 2),
    1, 1, 5,
    ROUND(COALESCE(lc.escalation_rate, 0.1000) * 100, 2),
    '合同基础台账补充',
    lc.remark,
    1, 'system', NOW(), 1, 0
FROM (
    SELECT 1 AS dummy
) d
LEFT JOIN eng_lease_contract lc
       ON lc.deleted = 0 AND lc.status = 1
      AND lc.tenant_name COLLATE utf8mb4_general_ci = '广州云帕供应链管理有限公司'
WHERE @yunpa_tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM biz_contract bc
      WHERE bc.deleted = 0
        AND bc.contract_type = 1
        AND bc.tenant_ref_id = @yunpa_tenant_id
  )
LIMIT 1;

-- 把合同基础台账中的云帕记录关联到正式合同，并确保租户ID一致
SET @yunpa_contract_id := (
    SELECT bc.id FROM biz_contract bc
    WHERE bc.deleted = 0
      AND bc.contract_type = 1
      AND bc.tenant_ref_id = @yunpa_tenant_id
    ORDER BY bc.id ASC LIMIT 1
);

UPDATE eng_lease_contract
SET biz_contract_id = @yunpa_contract_id,
    tenant_ref_id = COALESCE(tenant_ref_id, @yunpa_tenant_id),
    update_time = NOW()
WHERE deleted = 0
  AND status = 1
  AND tenant_name COLLATE utf8mb4_general_ci = '广州云帕供应链管理有限公司'
  AND @yunpa_contract_id IS NOT NULL;

-- 3) 缴费通知单抬头按缴费单位定制表
CREATE TABLE IF NOT EXISTS eng_notice_header_profile (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope_type               VARCHAR(20)  NOT NULL COMMENT 'LEASE(租金/物业费) | UTILITY(水电/垃圾费) | DEPOSIT(保证金)',
    tenant_name              VARCHAR(200) NOT NULL COMMENT '缴费单位名称',
    park_name                VARCHAR(200) NULL     COMMENT '园区名称',
    notice_prefix            VARCHAR(64)  NULL     COMMENT '单据编号前缀',
    period_start             DATE         NULL     COMMENT '计费期间起',
    period_end               DATE         NULL     COMMENT '计费期间止',
    issue_date               DATE         NULL     COMMENT '开具日期',
    issue_month_offset       INT          NULL     COMMENT '开具日期相对账期月偏移',
    issue_day                INT          NULL     COMMENT '开具日(1-31)',
    due_date                 DATE         NULL     COMMENT '缴费截止日',
    due_month_offset         INT          NULL     COMMENT '截止日相对账期月偏移',
    due_day                  INT          NULL     COMMENT '截止日(1-31)',
    contact_name             VARCHAR(100) NULL     COMMENT '水电费联系人',
    contact_phone            VARCHAR(64)  NULL     COMMENT '水电费联系电话',
    payment_account          TEXT         NULL     COMMENT '水电费收款账户/方式',
    rent_payee_name          VARCHAR(200) NULL     COMMENT '租金/租金保证金收取单位',
    rent_contact_name        VARCHAR(100) NULL     COMMENT '租金联系人',
    rent_contact_phone       VARCHAR(64)  NULL     COMMENT '租金联系电话',
    rent_payment_account     TEXT         NULL     COMMENT '租金收款账户/方式',
    property_payee_name      VARCHAR(200) NULL     COMMENT '物业费/物业保证金收取单位',
    property_contact_name    VARCHAR(100) NULL     COMMENT '物业费联系人',
    property_contact_phone   VARCHAR(64)  NULL     COMMENT '物业费联系电话',
    property_payment_account TEXT         NULL     COMMENT '物业费收款账户/方式',
    tenant_id                BIGINT       NOT NULL DEFAULT 1,
    create_by                VARCHAR(32),
    create_time              DATETIME,
    update_by                VARCHAR(32),
    update_time              DATETIME,
    version                  INT          NOT NULL DEFAULT 1,
    deleted                  TINYINT      NOT NULL DEFAULT 0,
    KEY idx_nhp_scope_tenant (scope_type, tenant_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='缴费通知单抬头-按缴费单位定制配置';
