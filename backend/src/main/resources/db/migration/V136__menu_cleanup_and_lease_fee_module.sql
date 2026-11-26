-- =====================================================================
-- V136:
--   1. 物业服务模块下线多余菜单与权限(会议室预约/品质核查/投诉建议/意见反馈/
--      投票问卷/物业活动/资源目录/资源预订/出入证管理/园区论坛/申报服务/
--      知产服务/政策服务/生态配置),取消「物业」「惠企」二级目录。
--   2. 「能耗」更名为「缴费管理」并整体迁至园区运营下,新增「租金」「物业费」菜单。
--   3. 新建租金/物业管理费月度计费与通知单表(eng_lease_contract /
--      eng_lease_fee_period / eng_lease_fee_entry / eng_lease_fee_notice /
--      eng_lease_fee_notice_item),并预置 DI PARK 11 份租户合同台账。
-- =====================================================================

-- ---------- 1. 下线物业服务多余功能菜单与权限(隐藏停用,保留历史表数据) ----------
UPDATE sys_menu SET visible = 0, status = 0
WHERE deleted = 0 AND (
    TRIM(BOTH '/' FROM path) IN (
        'property/meeting', 'property/check-quality', 'property/complaint', 'property/feedback',
        'property/survey', 'property/activity', 'rsv/resource', 'rsv/booking',
        'service/passcard', 'service/forum', 'service/declare', 'service/ip',
        'service/policy', 'service/ecosystem'
    )
    OR perm LIKE 'property:meeting:%'
    OR perm LIKE 'property:feedback:%'
    OR perm LIKE 'property:survey:%'
    OR perm LIKE 'property:activity:%'
    OR perm LIKE 'rsv:%'
    OR perm LIKE 'service:passcard:%'
    OR perm LIKE 'service:forum:%'
    OR perm LIKE 'service:declare:%'
    OR perm LIKE 'service:ip:%'
    OR perm LIKE 'service:policy:%'
    OR perm LIKE 'service:ecosystem:%'
);

-- 取消「物业」「惠企」两级目录外壳(历史目录项隐藏,下级保留菜单变为直属)
UPDATE sys_menu SET visible = 0, status = 0
WHERE deleted = 0 AND type = 1 AND name IN ('物业', '惠企') AND (path IS NULL OR path = '');

-- ---------- 2. 「能耗」更名为「缴费管理」,新增「租金」「物业费」菜单 ----------
UPDATE sys_menu SET name = '缴费管理', icon = 'Tickets'
WHERE deleted = 0 AND type = 1 AND name = '能耗';

SET @fee_parent = COALESCE(
    (SELECT id FROM sys_menu WHERE deleted = 0 AND type = 1 AND name = '缴费管理' ORDER BY id LIMIT 1),
    (SELECT parent_id FROM sys_menu WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'energy/electric' ORDER BY id LIMIT 1),
    0
);

CREATE TEMPORARY TABLE lease_fee_pages (path VARCHAR(64) NOT NULL, name VARCHAR(32) NOT NULL, sort INT NOT NULL);
INSERT INTO lease_fee_pages (path, name, sort) VALUES
('/energy/rent', '租金', 19),
('/energy/property', '物业费', 20);

INSERT INTO sys_menu (parent_id, name, type, path, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT @fee_parent, p.name, 2, p.path, p.sort, 1, 1, 1, 'system', NOW(), 1, 0
FROM lease_fee_pages p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE TRIM(BOTH '/' FROM m.path) = TRIM(BOTH '/' FROM p.path) AND m.deleted = 0
);

-- 已拥有电费/水费/垃圾处理费/缴费汇总任一页面的角色和用户,自动获得租金与物业费页面权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT g.role_id, page.id
FROM sys_role_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('energy/electric', 'energy/water', 'energy/garbage', 'energy/summary')
JOIN lease_fee_pages p
JOIN sys_menu page ON TRIM(BOTH '/' FROM page.path) = TRIM(BOTH '/' FROM p.path) AND page.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu e WHERE e.role_id = g.role_id AND e.menu_id = page.id
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT r.id, page.id
FROM sys_role r
JOIN lease_fee_pages p
JOIN sys_menu page ON TRIM(BOTH '/' FROM page.path) = TRIM(BOTH '/' FROM p.path) AND page.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = r.id AND e.menu_id = page.id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT g.user_id, page.id
FROM sys_user_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('energy/electric', 'energy/water', 'energy/garbage', 'energy/summary')
JOIN lease_fee_pages p
JOIN sys_menu page ON TRIM(BOTH '/' FROM page.path) = TRIM(BOTH '/' FROM p.path) AND page.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_menu e WHERE e.user_id = g.user_id AND e.menu_id = page.id
);
DROP TEMPORARY TABLE lease_fee_pages;

-- 访问日志路由模块映射
UPDATE route_module_mapping SET module_name = '缴费管理' WHERE module = 'energy';
INSERT INTO route_module_mapping (route, module, module_name, is_core, enabled)
SELECT '/energy/lease-fee/**', 'energy', '缴费管理', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM route_module_mapping WHERE route = '/energy/lease-fee/**');

-- ---------- 3. 租金与物业管理费计费合同台账 ----------
CREATE TABLE eng_lease_contract (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    seq_no               INT            NOT NULL DEFAULT 1 COMMENT '合同序号',
    project_id           BIGINT         NULL COMMENT '园区ID',
    tenant_ref_id        BIGINT         NULL COMMENT '关联租客ID(crm_tenant)',
    tenant_name          VARCHAR(128)   NOT NULL COMMENT '租户/公司名称',
    unit_name            VARCHAR(255)   NOT NULL COMMENT '楼层/区域',
    agreement_no         VARCHAR(255)   NULL COMMENT '协议编号(租赁/物业)',
    lease_area           DECIMAL(14,2)  NOT NULL DEFAULT 0 COMMENT '计租面积(㎡)',
    start_date           DATE           NOT NULL COMMENT '起租日',
    end_date             DATE           NOT NULL COMMENT '到期日(最后一日)',
    total_months         INT            NULL COMMENT '计租总月数',
    pricing_mode         VARCHAR(255)   NULL COMMENT '原始计价方式说明',
    rent_unit_price      DECIMAL(18,12) NOT NULL DEFAULT 0 COMMENT '折算月租金基础单价(元/㎡/月)',
    property_unit_price  DECIMAL(18,12) NOT NULL DEFAULT 2 COMMENT '折算月物业费基础单价(元/㎡/月)',
    escalation_rate      DECIMAL(8,4)   NOT NULL DEFAULT 0 COMMENT '递增比例(如 0.10=10%, 0.08=8%)',
    escalation_months    INT            NOT NULL DEFAULT 36 COMMENT '递增周期(月)',
    rent_deposit         DECIMAL(14,2)  NULL COMMENT '租金保证金(元)',
    property_deposit     DECIMAL(14,2)  NULL COMMENT '物业保证金(元)',
    collection_timing    VARCHAR(255)   NULL COMMENT '约定收款时点',
    deposit_terms        VARCHAR(512)   NULL COMMENT '保证金缴纳约定',
    rules_json           TEXT           NULL COMMENT '免租期/优惠期/周期免租规则JSON',
    status               TINYINT        NOT NULL DEFAULT 1 COMMENT '1在用 0停用',
    remark               VARCHAR(500)   NULL,
    tenant_id            BIGINT         NOT NULL DEFAULT 1,
    create_by            VARCHAR(32)    NULL,
    create_time          DATETIME       NULL,
    update_by            VARCHAR(32)    NULL,
    update_time          DATETIME       NULL,
    version              INT            NOT NULL DEFAULT 1,
    deleted              TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_lease_contract_tenant (tenant_name),
    KEY idx_lease_contract_status (status, seq_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租金与物业费计费合同台账';

-- ---------- 4. 月度租金与物业管理费账期(一月一条,租金与物业费收取单位分开配置) ----------
CREATE TABLE eng_lease_fee_period (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    project_id               BIGINT        NULL,
    period                   VARCHAR(7)    NOT NULL COMMENT '账期 yyyy-MM',
    period_start             DATE          NULL COMMENT '计费期间起',
    period_end               DATE          NULL COMMENT '计费期间止',
    status                   VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿 CONFIRMED已确认出账',
    park_name                VARCHAR(128)  NULL COMMENT '园区名称',
    notice_prefix            VARCHAR(32)   NULL COMMENT '单据编号前缀',
    rent_payee_name          VARCHAR(128)  NULL COMMENT '租金收取单位名称',
    rent_payment_account     VARCHAR(512)  NULL COMMENT '租金收款账户/方式',
    rent_contact_name        VARCHAR(64)   NULL COMMENT '租金联系人',
    rent_contact_phone       VARCHAR(32)   NULL COMMENT '租金联系电话',
    property_payee_name      VARCHAR(128)  NULL COMMENT '物业管理费收取单位名称',
    property_payment_account VARCHAR(512)  NULL COMMENT '物业管理费收款账户/方式',
    property_contact_name    VARCHAR(64)   NULL COMMENT '物业费联系人',
    property_contact_phone   VARCHAR(32)   NULL COMMENT '物业费联系电话',
    issue_date               DATE          NULL COMMENT '开具日期',
    due_date                 DATE          NULL COMMENT '缴费截止日',
    remark                   VARCHAR(255)  NULL,
    rent_total_amount        DECIMAL(16,2) NULL COMMENT '租金应收合计快照',
    property_total_amount    DECIMAL(16,2) NULL COMMENT '物业费应收合计快照',
    notice_count             INT           NULL COMMENT '通知单总数(租金+物业费)',
    generated_time           DATETIME      NULL,
    inputs_changed_time      DATETIME      NULL,
    snapshot_json            MEDIUMTEXT    NULL COMMENT '确认出账冻结工作表JSON',
    tenant_id                BIGINT        NOT NULL DEFAULT 1,
    create_by                VARCHAR(32)   NULL,
    create_time              DATETIME      NULL,
    update_by                VARCHAR(32)   NULL,
    update_time              DATETIME      NULL,
    version                  INT           NOT NULL DEFAULT 1,
    deleted                  TINYINT       NOT NULL DEFAULT 0,
    active_key               VARCHAR(32) GENERATED ALWAYS AS (
        IF(deleted = 0, CONCAT(IFNULL(project_id, 0), '|', period), NULL)
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lease_fee_period_active (active_key),
    KEY idx_lease_fee_period (period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '月度租金与物业管理费账期';

-- ---------- 5. 月度合同减免系数与手工调整(每个合同每月可独立调整) ----------
CREATE TABLE eng_lease_fee_entry (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    lease_period_id          BIGINT        NOT NULL,
    period                   VARCHAR(7)    NOT NULL,
    contract_id              BIGINT        NOT NULL,
    rent_factor              DECIMAL(12,8) NULL COMMENT '租金减免系数(1=全额 0.5=5折 0=免租)',
    property_factor          DECIMAL(12,8) NULL COMMENT '物业费减免系数',
    rent_amount_override     DECIMAL(14,2) NULL COMMENT '手工覆盖应付租金',
    property_amount_override DECIMAL(14,2) NULL COMMENT '手工覆盖应付物业费',
    remark                   VARCHAR(500)  NULL,
    tenant_id                BIGINT        NOT NULL DEFAULT 1,
    create_by                VARCHAR(32)   NULL,
    create_time              DATETIME      NULL,
    update_by                VARCHAR(32)   NULL,
    update_time              DATETIME      NULL,
    version                  INT           NOT NULL DEFAULT 1,
    deleted                  TINYINT       NOT NULL DEFAULT 0,
    active_key               VARCHAR(48) GENERATED ALWAYS AS (
        IF(deleted = 0, CONCAT(lease_period_id, '|', contract_id), NULL)
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lease_fee_entry_active (active_key),
    KEY idx_lease_fee_entry_period (lease_period_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '月度租金物业费调整明细';

-- ---------- 6. 租金/物业管理费缴费通知单(租金与物业费因收取单位不同分别出单) ----------
CREATE TABLE eng_lease_fee_notice (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    lease_period_id  BIGINT        NOT NULL,
    period           VARCHAR(7)    NOT NULL,
    fee_category     VARCHAR(16)   NOT NULL COMMENT 'RENT租金 / PROPERTY物业管理费',
    notice_no        VARCHAR(64)   NULL COMMENT '单据编号',
    payee_name       VARCHAR(128)  NULL COMMENT '收取单位名称',
    payment_account  VARCHAR(512)  NULL COMMENT '收款账户/方式',
    tenant_ref_id    BIGINT        NULL,
    tenant_name      VARCHAR(128)  NULL,
    unit_name        VARCHAR(255)  NULL,
    agreement_no     VARCHAR(255)  NULL,
    lease_area       DECIMAL(14,2) NULL,
    standard_amount  DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '标准金额(未计减免)',
    payable_amount   DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '减免后应付金额',
    other_fee        DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '其他费用(手工调整)',
    subtotal         DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '费用小计',
    paid_amount      DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '已付金额',
    prior_arrears    DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '前期欠费',
    total_due        DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本期应收合计',
    bill_id          BIGINT        NULL COMMENT '确认出账后指向fin_bill',
    payment_status   TINYINT       NOT NULL DEFAULT 0 COMMENT '缴费登记:0未缴费 1已缴费',
    sort             INT           NOT NULL DEFAULT 0,
    remark           VARCHAR(500)  NULL,
    tenant_id        BIGINT        NOT NULL DEFAULT 1,
    create_by        VARCHAR(32)   NULL,
    create_time      DATETIME      NULL,
    update_by        VARCHAR(32)   NULL,
    update_time      DATETIME      NULL,
    version          INT           NOT NULL DEFAULT 1,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_lease_notice_period (lease_period_id, fee_category),
    KEY idx_lease_notice_tenant (tenant_ref_id, period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租金与物业管理费缴费通知单';

CREATE TABLE eng_lease_fee_notice_item (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    notice_id       BIGINT         NOT NULL,
    lease_period_id BIGINT         NOT NULL,
    contract_id     BIGINT         NULL,
    seq             VARCHAR(8)     NULL,
    item_type       VARCHAR(32)    NOT NULL COMMENT 'RENT / PROPERTY / CUSTOM',
    manual_flag     TINYINT        NOT NULL DEFAULT 0,
    fee_item        VARCHAR(128)   NOT NULL,
    unit_name       VARCHAR(255)   NULL,
    lease_area      DECIMAL(14,2)  NULL,
    unit_price      DECIMAL(18,6)  NULL,
    standard_amount DECIMAL(14,2)  NULL,
    discount_factor DECIMAL(12,8)  NULL,
    amount          DECIMAL(14,2)  NULL,
    remark          VARCHAR(500)   NULL,
    sort            INT            NOT NULL DEFAULT 0,
    tenant_id       BIGINT         NOT NULL DEFAULT 1,
    create_by       VARCHAR(32)    NULL,
    create_time     DATETIME       NULL,
    update_by       VARCHAR(32)    NULL,
    update_time     DATETIME       NULL,
    version         INT            NOT NULL DEFAULT 1,
    deleted         TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_lease_item_notice (notice_id),
    KEY idx_lease_item_period (lease_period_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租金与物业管理费通知单明细';

-- ---------- 7. 预置 DI PARK 11 份租户合同台账(依据确认版《逐月应付台账》) ----------
INSERT INTO eng_lease_contract (
    seq_no, tenant_name, unit_name, agreement_no, lease_area, start_date, end_date, total_months,
    pricing_mode, rent_unit_price, property_unit_price, escalation_rate, escalation_months,
    rent_deposit, property_deposit, collection_timing, deposit_terms, rules_json, status,
    tenant_id, create_by, create_time, version, deleted
)
SELECT * FROM (
    SELECT 1 AS seq_no, '广东分享智联供应链管理有限公司' AS tenant_name, 'DIPARK第六层的储存区一' AS unit_name, '待补充' AS agreement_no,
           4700.00 AS lease_area, '2026-05-01' AS start_date, '2032-04-30' AS end_date, 72 AS total_months,
           '租金按日计租(已按每月30天折算)；物业费按月计费(2元/㎡/月，统一口径)' AS pricing_mode,
           12.000000000000 AS rent_unit_price, 2.000000000000 AS property_unit_price, 0.1000 AS escalation_rate, 36 AS escalation_months,
           131600.00 AS rent_deposit, NULL AS property_deposit, '当月30日前收取下个月租金' AS collection_timing,
           '签订合同3日内一次性缴纳' AS deposit_terms,
           '{"periods":[{"from":"2026-05","to":"2026-05","rentFactor":0,"propertyFactor":0,"remark":"免租期"},{"from":"2026-06","to":"2026-09","rentFactor":0.5,"propertyFactor":0.5,"remark":"优惠期，租金及物业费按5折收取"}]}' AS rules_json,
           1 AS status, 1 AS tenant_id, 'system' AS create_by, NOW() AS create_time, 1 AS version, 0 AS deleted
    UNION ALL
    SELECT 2, '广州昌泰供应链科技有限公司', 'DIPARK第五层', '待补充',
           13500.00, '2026-06-01', '2032-05-31', 72,
           '租金按日计租(已按每月30天折算)；物业费按月计费(2元/㎡/月，统一口径)',
           12.000000000000, 2.000000000000, 0.1000, 36,
           378000.00, NULL, '当月30日前收取下个月租金',
           '分两期：签约3天内189,000元；2026-07-31前189,000元',
           '{"periods":[{"from":"2026-06","to":"2026-07","rentFactor":0,"propertyFactor":0,"remark":"免租期"},{"from":"2026-08","to":"2026-10","rentFactor":0,"propertyFactor":1,"remark":"搬迁补助抵扣租金"}],"everyPaidMonthsHalfRent":{"paidCount":12,"rentFactor":0.5,"propertyFactor":1,"remark":"每交租满12个月，本月租金按5折收取（0.5个月免租）"}}',
           1, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT 3, '广州微新供应链管理有限公司', 'DIPARK第四层', '待补充',
           13500.00, '2026-07-01', '2032-06-30', 72,
           '租金按月计租',
           12.800000000000, 2.000000000000, 0.1000, 36,
           345600.00, 54000.00, '当月30日前收取下个月租金',
           '租金、物业保证金均于签约3日内一次性缴纳',
           '{"periods":[{"from":"2026-07","to":"2026-08","rentFactor":0,"propertyFactor":0,"remark":"免租期"},{"from":"2026-09","to":"2026-11","rentFactor":0,"propertyFactor":0,"remark":"免租期/搬迁补助抵扣租金（物业费同免）"}]}',
           1, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT 4, '中印国际供应链（广州）有限公司', 'DIPARK第一层防火分区三', '待补充',
           3500.00, '2026-06-01', '2032-05-31', 72,
           '租金按月计租',
           34.000000000000, 2.000000000000, 0.1000, 36,
           238000.00, 14000.00, '当月30日前收取下个月租金',
           '租金、物业保证金均于签约7日内一次性缴纳',
           '{"periods":[{"from":"2026-06","to":"2026-07","rentFactor":0,"propertyFactor":0,"remark":"免租期"},{"from":"2026-08","to":"2026-09","rentFactor":0.7,"propertyFactor":0.7,"remark":"优惠期，租金及物业费按7折收取"}]}',
           1, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT 5, '广东云山供应链科技有限公司', 'DI PARK数智云仓产业园第二层', 'GZCJ-(DI PARK)-20260519-001-补01 / WYFW-(DI PARK)-20260519-001-补01',
           13500.00, '2026-06-01', '2041-05-31', 180,
           '租金按月计租',
           19.000000000000, 2.000000000000, 0.0800, 36,
           513000.00, 54000.00, '当月13号前缴纳当月租金',
           '自2027年1月起分12个月平均支付：租金保证金42,750元/月，物业保证金4,500元/月',
           '{"periods":[{"from":"2026-06","to":"2026-10","rentFactor":0,"propertyFactor":0,"remark":"免租期/搬迁补助抵扣（物业费免缴期内）"},{"from":"2026-11","to":"2026-11","rentFactor":0,"propertyFactor":0,"remark":"免租期（首个年度）"}],"annualMonthFree":{"fromYear":2027,"toYear":2040,"month":12,"rentFactor":0,"propertyFactor":1,"remark":"每年最后一个月免租一个月（按自然年度12月，租金全免，物业费正常收取）"}}',
           1, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT 6, '广东云山供应链科技有限公司', 'DI PARK数智云仓产业园第三层', 'GZCJ-(DI PARK)-20260519-002-补01 / WYFW-(DI PARK)-20260519-002-补01',
           13500.00, '2026-06-01', '2041-05-31', 180,
           '租金按月计租',
           12.500000000000, 2.000000000000, 0.0800, 36,
           337500.00, 54000.00, '当月13号前缴纳当月租金',
           '自2027年1月起分12个月平均支付：租金保证金28,125元/月，物业保证金4,500元/月',
           '{"periods":[{"from":"2026-06","to":"2026-10","rentFactor":0,"propertyFactor":0,"remark":"免租期/搬迁补助抵扣（物业费免缴期内）"},{"from":"2026-11","to":"2026-11","rentFactor":0,"propertyFactor":0,"remark":"免租期（首个年度）"}],"annualMonthFree":{"fromYear":2027,"toYear":2040,"month":12,"rentFactor":0,"propertyFactor":1,"remark":"每年最后一个月免租一个月（按自然年度12月，租金全免，物业费正常收取）"}}',
           1, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT 7, '广东云山供应链科技有限公司', 'DI PARK数智云仓产业园第六层储存区二及储存区三', 'GZCJ-(DI PARK)-20260519-003-补01 / WYFW-(DI PARK)-20260519-003-补01',
           8800.00, '2026-07-01', '2041-06-30', 180,
           '租金按月计租',
           12.000000000000, 2.000000000000, 0.0800, 36,
           211200.00, 35200.00, '当月13号前缴纳当月租金',
           '自2027年1月起分12个月平均支付：租金保证金17,600元/月，物业保证金2,934元/月（末期2,926元）',
           '{"periods":[{"from":"2026-07","to":"2026-11","rentFactor":0,"propertyFactor":0,"remark":"免租期/搬迁补助抵扣（物业费免缴期内）"},{"from":"2026-12","to":"2026-12","rentFactor":0,"propertyFactor":0,"remark":"免租期（首个年度）"}],"annualMonthFree":{"fromYear":2027,"toYear":2040,"month":12,"rentFactor":0,"propertyFactor":1,"remark":"每年最后一个月免租一个月（按自然年度12月，租金全免，物业费正常收取）"}}',
           1, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT 8, '广东云山供应链科技有限公司', 'DI PARK数智云仓产业园第七层', 'GZCJ-(DI PARK)-20260519-004-补01 / WYFW-(DI PARK)-20260519-004-补01',
           13500.00, '2026-07-01', '2041-06-30', 180,
           '租金按月计租',
           12.000000000000, 2.000000000000, 0.0800, 36,
           324000.00, 54000.00, '当月13号前缴纳当月租金',
           '自2027年1月起分12个月平均支付：租金保证金27,000元/月，物业保证金4,500元/月',
           '{"periods":[{"from":"2026-07","to":"2026-11","rentFactor":0,"propertyFactor":0,"remark":"免租期/搬迁补助抵扣（物业费免缴期内）"},{"from":"2026-12","to":"2026-12","rentFactor":0,"propertyFactor":0,"remark":"免租期（首个年度）"}],"annualMonthFree":{"fromYear":2027,"toYear":2040,"month":12,"rentFactor":0,"propertyFactor":1,"remark":"每年最后一个月免租一个月（按自然年度12月，租金全免，物业费正常收取）"}}',
           1, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT 9, '广东云山供应链科技有限公司', 'DI PARK数智云仓产业园首层B区', 'GZCJ-(DI PARK)-20260701-004 / WYFW-(DI PARK)-20260701-004',
           3600.00, '2026-08-08', '2041-08-07', 180,
           '租金按月计租（合同月租金102,800元反算单价）',
           28.555555555556, 2.000000000000, 0.0800, 36,
           205600.00, 14400.00, '暂按当月30日前收取下个月租金（待确认）',
           '租金保证金：2026-09-30前68,500元、2026-10-31前68,500元、2027-11-30前68,600元；物业保证金：同期各4,800元',
           '{"periods":[{"from":"2026-08","to":"2026-12","rentFactor":0,"propertyFactor":0,"remark":"免租期（2026-08-08至2026-12-31）；免租期内租金、物业费均按0（合同未另设物业免缴期，待确认）"}],"annualMonthFree":{"fromYear":2027,"toYear":2040,"month":12,"rentFactor":0,"propertyFactor":0,"remark":"每年12月免租（租金全免；物业费暂按同免处理，待确认）"}}',
           1, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT 10, '广州鑫晨供应链有限公司', 'DIPARK第五层储存区二及储存区三', 'GZCI-(DIPARK)-20260713-002 / WYFW-(DIPARK)-20260713-002',
           9000.00, '2026-08-01', '2041-06-30', 179,
           '租金按月计租',
           13.000000000000, 2.000000000000, 0.1000, 36,
           234000.00, 36000.00, '当月30日前收取下个月租金',
           '合同台账未载明分期，按一次性缴纳理解',
           '{"periods":[{"from":"2026-08","to":"2026-09","rentFactor":0,"propertyFactor":0,"remark":"免租期（2026-08-01至2026-09-30）"},{"from":"2027-12","to":"2027-12","rentFactor":0,"propertyFactor":0,"remark":"免租期（2027-12-01至2027-12-31；物业费暂按同免处理，待确认）"},{"from":"2028-12","to":"2028-12","rentFactor":0,"propertyFactor":0,"remark":"免租期（2028-12-01至2028-12-31；物业费暂按同免处理，待确认）"}]}',
           1, 1, 'system', NOW(), 1, 0
    UNION ALL
    SELECT 11, '广州云帕供应链管理有限公司', 'DI PARK数智云仓产业园首层A区', 'GZCJ-(DI PARK)-20260701-003 / WYFW-(DI PARK)-20260701-003',
           3300.00, '2026-07-01', '2032-06-30', 72,
           '租金按月计租',
           30.000000000000, 2.000000000000, 0.1000, 36,
           198000.00, 13200.00, '当月30日前收取下个月租金',
           '2026年12月31日前缴纳完成',
           '{"periods":[{"from":"2026-07","to":"2026-11","rentFactor":0,"propertyFactor":0,"remark":"免租期（2026-07-01至2026-11-30）"}]}',
           1, 1, 'system', NOW(), 1, 0
) seed
WHERE NOT EXISTS (SELECT 1 FROM eng_lease_contract WHERE deleted = 0 LIMIT 1);
