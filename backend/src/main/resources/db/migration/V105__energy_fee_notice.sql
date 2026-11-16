-- =====================================================================
-- V105 能耗模块改版:下线「水电公摊 / 水电结算」,新增 电费 / 水费 / 垃圾处理费 / 缴费汇总
--
-- 背景(2026-11-15 负责人线下表《租户对账版 应收收费明细表》):
--   园区每月只填三类数:① 电费总度数(南网用电 + 光伏用电,园区有光伏)
--   ② 每块租户表的本期读数 ③ 生活垃圾 / 工业垃圾清运总额。
--   系统据此自动算出每家租户的 水费 / 公摊水费 / 电费 / 公摊电费 / 垃圾费,
--   并生成每户一张的《缴费通知单》与一张《应收收费明细表》(缴费汇总)。
--
-- 口径(与线下表一致):
--   电: 公摊度数 = 南网 + 光伏 − 全部分表用量;按各参与公摊租户用量占比摊回。
--       室外充电桩、未租单元(屋顶)只付自身用量,不参与公摊(eng_meter.share_public = 0)。
--   水: 公摊度数 = 总表度数 − 租户分表合计;同样按用量占比摊回。
--   垃圾: 生活 / 工业两笔总额按各单元「计租面积」占比分摊,只有园区租户参与。
--
-- 本迁移只加列、建表、登记菜单;不动任何存量业务数据。
-- eng_utility_bill / eng_allocation 两张旧表保留不删(历史数据可查),代码已下线。
-- =====================================================================

-- ---------- 表计补两列:是否参与公摊、所在单元计租面积 ----------
ALTER TABLE eng_meter
    ADD COLUMN share_public TINYINT NOT NULL DEFAULT 1
        COMMENT '参与公摊与垃圾费分摊:1=园区租户 0=不参与(室外充电桩/未租单元等只付自身用量)' AFTER meter_role,
    ADD COLUMN lease_area DECIMAL(12,2) NULL
        COMMENT '所在单元(区域)计租面积㎡,垃圾费按此分摊;同一区域多块表取其中最大值' AFTER customer_name;

-- ---------- 月度缴费账期:一个园区一个月一条,装当月手工录入的总量与通知单抬头 ----------
CREATE TABLE eng_fee_period (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    project_id                BIGINT        NULL COMMENT '园区',
    period                    VARCHAR(7)    NOT NULL COMMENT '账期 yyyy-MM',
    period_start              DATE          NULL COMMENT '计费期间起',
    period_end                DATE          NULL COMMENT '计费期间止',
    status                    VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿 CONFIRMED已确认出账',
    park_name                 VARCHAR(128)  NULL COMMENT '通知单上的园区名称',
    notice_prefix             VARCHAR(32)   NULL COMMENT '单据编号前缀,如 DIPARK',
    electric_grid_usage       DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '南网用电总度数',
    electric_solar_usage      DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '光伏发电园区用电度数',
    electric_price            DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '电费单价 元/度',
    water_total_usage         DECIMAL(16,2) NULL COMMENT '水总表度数;为空时按发票总表(MAIN)当月抄表取',
    water_price               DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '水费单价 元/m³',
    domestic_garbage_amount   DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '生活垃圾清运总额',
    domestic_garbage_note     VARCHAR(255)  NULL COMMENT '生活垃圾说明,如 6桶*13.35元/桶',
    industrial_garbage_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '工业垃圾清运总额',
    industrial_garbage_note   VARCHAR(255)  NULL,
    issue_date                DATE          NULL COMMENT '开具日期',
    due_date                  DATE          NULL COMMENT '缴费截止日',
    contact_name              VARCHAR(64)   NULL,
    contact_phone             VARCHAR(32)   NULL,
    payment_account           VARCHAR(512)  NULL COMMENT '收款账户/方式(多行)',
    remark                    VARCHAR(255)  NULL,
    -- 下面几列是最近一次生成通知单时的汇总快照,方便列表页直接看
    electric_submeter_usage   DECIMAL(16,2) NULL COMMENT '电:全部分表用量合计',
    electric_public_usage     DECIMAL(16,2) NULL COMMENT '电:公摊度数',
    water_used_total          DECIMAL(16,2) NULL COMMENT '水:实际采用的总表度数',
    water_submeter_usage      DECIMAL(16,2) NULL COMMENT '水:分表用量合计',
    water_public_usage        DECIMAL(16,2) NULL COMMENT '水:公摊度数',
    total_lease_area          DECIMAL(14,2) NULL COMMENT '参与垃圾分摊的计租面积合计',
    notice_count              INT           NULL,
    total_amount              DECIMAL(16,2) NULL COMMENT '本期应收合计',
    generated_time            DATETIME      NULL,
    tenant_id                 BIGINT        NOT NULL DEFAULT 1,
    create_by                 VARCHAR(32)   NULL,
    create_time               DATETIME      NULL,
    update_by                 VARCHAR(32)   NULL,
    update_time               DATETIME      NULL,
    version                   INT           NOT NULL DEFAULT 1,
    deleted                   TINYINT       NOT NULL DEFAULT 0,
    active_key                VARCHAR(32) GENERATED ALWAYS AS (
        IF(deleted = 0, CONCAT(IFNULL(project_id, 0), '|', period), NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_period_active (active_key),
    KEY idx_fee_period (period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='月度缴费账期(水电垃圾)';

-- ---------- 缴费通知单:一户(租户 × 楼层单元)一张 ----------
CREATE TABLE eng_fee_notice (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    fee_period_id           BIGINT        NOT NULL,
    period                  VARCHAR(7)    NOT NULL,
    notice_no               VARCHAR(64)   NULL COMMENT '单据编号',
    tenant_ref_id           BIGINT        NULL COMMENT '租户(经房间→合同反查),查不到时为空,出账会跳过',
    tenant_name             VARCHAR(128)  NULL COMMENT '租户/公司名称',
    unit_name               VARCHAR(128)  NULL COMMENT '厂房/单元,如 2层A~C区',
    floor_key               VARCHAR(32)   NULL COMMENT '分组用楼层键',
    lease_area              DECIMAL(12,2) NULL COMMENT '该户计租面积合计',
    share_public            TINYINT       NOT NULL DEFAULT 1,
    water_usage             DECIMAL(16,2) NOT NULL DEFAULT 0,
    water_public_usage      DECIMAL(16,4) NOT NULL DEFAULT 0,
    water_fee               DECIMAL(14,2) NOT NULL DEFAULT 0,
    water_public_fee        DECIMAL(14,2) NOT NULL DEFAULT 0,
    electric_usage          DECIMAL(16,2) NOT NULL DEFAULT 0,
    electric_public_usage   DECIMAL(16,4) NOT NULL DEFAULT 0,
    electric_fee            DECIMAL(14,2) NOT NULL DEFAULT 0,
    electric_public_fee     DECIMAL(14,2) NOT NULL DEFAULT 0,
    domestic_garbage_fee    DECIMAL(14,2) NOT NULL DEFAULT 0,
    industrial_garbage_fee  DECIMAL(14,2) NOT NULL DEFAULT 0,
    other_fee               DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '其他费用(如税金),手工填',
    other_fee_note          VARCHAR(128)  NULL,
    subtotal                DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '费用小计',
    paid_amount             DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '已付金额(手工)',
    prior_arrears           DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '前期欠费(手工)',
    total_due               DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '本期应收合计 = 小计 − 已付 + 前期欠费',
    bill_id                 BIGINT        NULL COMMENT '确认出账后指向 fin_bill',
    sort                    INT           NOT NULL DEFAULT 0,
    remark                  VARCHAR(255)  NULL,
    tenant_id               BIGINT        NOT NULL DEFAULT 1,
    create_by               VARCHAR(32)   NULL,
    create_time             DATETIME      NULL,
    update_by               VARCHAR(32)   NULL,
    update_time             DATETIME      NULL,
    version                 INT           NOT NULL DEFAULT 1,
    deleted                 TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_fee_notice_period (fee_period_id),
    KEY idx_fee_notice_tenant (tenant_ref_id, period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='缴费通知单';

-- ---------- 通知单明细行:与纸质通知单一行一行对应 ----------
CREATE TABLE eng_fee_notice_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    notice_id       BIGINT        NOT NULL,
    fee_period_id   BIGINT        NOT NULL,
    seq             VARCHAR(8)    NULL COMMENT '序号,子行如 3.1',
    item_type       VARCHAR(32)   NOT NULL COMMENT 'WATER/WATER_PUBLIC/ELECTRIC/ELECTRIC_PUBLIC/GARBAGE_DOMESTIC/GARBAGE_INDUSTRIAL/OTHER;_DETAIL 后缀为分表子行(不计入合计)',
    fee_item        VARCHAR(64)   NOT NULL COMMENT '费用项目',
    meter_id        BIGINT        NULL,
    meter_code      VARCHAR(64)   NULL,
    ratio           DECIMAL(10,2) NULL,
    prev_reading    DECIMAL(16,2) NULL,
    curr_reading    DECIMAL(16,2) NULL,
    quantity        DECIMAL(16,4) NULL COMMENT '计费数量',
    unit            VARCHAR(16)   NULL,
    unit_price      DECIMAL(10,4) NULL,
    amount          DECIMAL(14,2) NULL,
    remark          VARCHAR(255)  NULL,
    sort            INT           NOT NULL DEFAULT 0,
    tenant_id       BIGINT        NOT NULL DEFAULT 1,
    create_by       VARCHAR(32)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(32)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 1,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_fee_item_notice (notice_id),
    KEY idx_fee_item_period (fee_period_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='缴费通知单明细';

-- ---------- 访问日志的路由-模块映射 ----------
INSERT INTO route_module_mapping (route, module, module_name, is_core, enabled)
SELECT '/energy/fee-period/**', 'energy', '能源管理', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM route_module_mapping WHERE route = '/energy/fee-period/**');

-- ---------- 菜单:四个新页面挂在「智能表计」同一个目录下;原先有表计页权限的角色/用户自动获得 ----------
CREATE TEMPORARY TABLE energy_fee_pages (path VARCHAR(64) NOT NULL, name VARCHAR(32) NOT NULL, sort INT NOT NULL);
INSERT INTO energy_fee_pages (path, name, sort) VALUES
('/energy/electric', '电费', 21),
('/energy/water', '水费', 22),
('/energy/garbage', '垃圾处理费', 23),
('/energy/summary', '缴费汇总', 24);

INSERT INTO sys_menu (parent_id, name, type, path, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT COALESCE((SELECT parent_id FROM sys_menu
                 WHERE TRIM(BOTH '/' FROM path) = 'energy/meter' AND deleted = 0 ORDER BY id LIMIT 1), 0),
       p.name, 2, p.path, p.sort, 1, 1, 1, 'system', NOW(), 1, 0
FROM energy_fee_pages p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE TRIM(BOTH '/' FROM m.path) = TRIM(BOTH '/' FROM p.path) AND m.deleted = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT old_grant.role_id, page.id
FROM sys_role_menu old_grant
JOIN sys_menu meter ON meter.id = old_grant.menu_id
    AND TRIM(BOTH '/' FROM meter.path) = 'energy/meter' AND meter.type <> 3 AND meter.deleted = 0
JOIN energy_fee_pages p
JOIN sys_menu page ON TRIM(BOTH '/' FROM page.path) = TRIM(BOTH '/' FROM p.path) AND page.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu existing
                  WHERE existing.role_id = old_grant.role_id AND existing.menu_id = page.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT r.id, page.id
FROM sys_role r
JOIN energy_fee_pages p
JOIN sys_menu page ON TRIM(BOTH '/' FROM page.path) = TRIM(BOTH '/' FROM p.path) AND page.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id = r.id AND existing.menu_id = page.id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT old_grant.user_id, page.id
FROM sys_user_menu old_grant
JOIN sys_menu meter ON meter.id = old_grant.menu_id
    AND TRIM(BOTH '/' FROM meter.path) = 'energy/meter' AND meter.type <> 3 AND meter.deleted = 0
JOIN energy_fee_pages p
JOIN sys_menu page ON TRIM(BOTH '/' FROM page.path) = TRIM(BOTH '/' FROM p.path) AND page.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_menu existing
                  WHERE existing.user_id = old_grant.user_id AND existing.menu_id = page.id);
DROP TEMPORARY TABLE energy_fee_pages;

-- 旧「公摊测算」权限点与页面入口下线(只隐藏停用,不删行)
UPDATE sys_menu SET visible = 0, status = 0
WHERE deleted = 0 AND (perm = 'energy:allocation:calc'
   OR TRIM(BOTH '/' FROM path) IN ('energy/allocation', 'energy/settlement'));
