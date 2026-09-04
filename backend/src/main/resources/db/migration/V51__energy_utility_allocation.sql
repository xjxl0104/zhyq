-- V51: 公用事业设施费用分摊(2026-09-04 负责人给的合同《附件二》口径)
--
-- 背景:园区只有一张对外的电费/水费发票(总表口径),租户各自有分表。
--   总表用量 - 各租户分表 - 物业公司分表 = 公共区域用量 + 线损,这部分要按各家
--   实际用量占比摊回去。原来系统只有「表计 + 抄表」,没有总表概念,也没有分摊,
--   抄表记录里那个 fee 只是分表用量 × 某个单价,收不到公摊那部分。
--
-- 《附件二》原文公式(以电为例,水同理):
--   ① 当月不含税单价 = 当月电费发票不含税总额 / 当月电费发票总用电量
--   ② 分摊系数 = (公共区域用电量 + 损耗) / (园区现有租户实际抄表用电量 + 物业公司实际抄表用电量)
--      其中 (公共区域用电量 + 损耗) = 当月账单总用电量 - 租户实际抄表用电量 - 物业公司实际抄表用电量
--   ③ 乙方公摊电费 = 单价 × (乙方实际用电量 × 分摊系数) × (1 + 税率)
--   ④ 乙方总电费   = 乙方用电量 × 单价 × (1 + 税率) + 乙方公摊电费
--
-- 纯新增:一个加列 + 两张新表,不改任何存量数据。存量表计由 DEFAULT 落到「租户表」。

-- ---------- 表计角色:分摊的分母/分子靠它区分 ----------
-- VARCHAR(16) 够放最长的 'PROPERTY'(8);枚举列宽算错会被 MySQL 静默截断,这里留足
ALTER TABLE eng_meter
    ADD COLUMN meter_role VARCHAR(16) NOT NULL DEFAULT 'TENANT'
        COMMENT '表计角色:TENANT=租户分表 MAIN=园区总表(对应对外发票) PROPERTY=物业公司分表' AFTER energy_type;

CREATE INDEX idx_meter_role ON eng_meter (meter_role, energy_type);

-- ---------- 月度公用事业账单(对外发票口径) ----------
CREATE TABLE eng_utility_bill (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    project_id            BIGINT       NULL COMMENT '园区',
    energy_type           VARCHAR(16)  NOT NULL COMMENT '电/水,与 eng_meter.energy_type 同域',
    period                VARCHAR(7)   NOT NULL COMMENT '账期 yyyy-MM',
    invoice_usage         DECIMAL(16,4) NOT NULL DEFAULT 0 COMMENT '发票总用量(公式①②的分母/被减数)',
    invoice_amount_ex_tax DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '发票不含税总额',
    tax_rate              DECIMAL(6,2)  NOT NULL DEFAULT 0 COMMENT '税率%',
    status                VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿 CONFIRMED已确认(已出账)',
    remark                VARCHAR(255)  NULL,
    tenant_id             BIGINT       NOT NULL DEFAULT 1,
    create_by             VARCHAR(32)  NULL,
    create_time           DATETIME     NULL,
    update_by             VARCHAR(32)  NULL,
    update_time           DATETIME     NULL,
    version               INT          NOT NULL DEFAULT 1,
    deleted               TINYINT      NOT NULL DEFAULT 0,
    -- 同园区同能源同月只允许一张有效账单;逻辑删除后自动让出该键
    active_key            VARCHAR(64) GENERATED ALWAYS AS (
        IF(deleted = 0, CONCAT(IFNULL(project_id, 0), '|', energy_type, '|', period), NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_utility_bill_active (active_key),
    KEY idx_utility_bill_period (period, energy_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='月度公用事业账单(发票口径)';

-- ---------- 分摊结果:一行 = 某月某块分表该付多少 ----------
CREATE TABLE eng_allocation (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    utility_bill_id   BIGINT        NOT NULL COMMENT '所属月度账单',
    period            VARCHAR(7)    NOT NULL,
    energy_type       VARCHAR(16)   NOT NULL,
    meter_id          BIGINT        NOT NULL,
    meter_role        VARCHAR(16)   NOT NULL COMMENT '算这一行时该表计的角色',
    tenant_ref_id     BIGINT        NULL COMMENT '租户;物业公司分表可为空',
    own_usage         DECIMAL(16,4) NOT NULL DEFAULT 0 COMMENT '自身抄表用量',
    alloc_coefficient DECIMAL(18,8) NOT NULL DEFAULT 0 COMMENT '分摊系数(公式②),同一账期各家相同',
    alloc_usage       DECIMAL(16,4) NOT NULL DEFAULT 0 COMMENT '分摊用量 = 自身用量 × 系数',
    unit_price_ex_tax DECIMAL(16,6) NOT NULL DEFAULT 0 COMMENT '当月不含税单价(公式①)',
    tax_rate          DECIMAL(6,2)  NOT NULL DEFAULT 0,
    own_fee           DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '自身费用 = 自身用量 × 单价 × (1+税率)',
    alloc_fee         DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '公摊费用(公式③)',
    total_fee         DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '总费用(公式④)',
    bill_id           BIGINT        NULL COMMENT '出账后指向 fin_bill',
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    create_by         VARCHAR(32)   NULL,
    create_time       DATETIME      NULL,
    update_by         VARCHAR(32)   NULL,
    update_time       DATETIME      NULL,
    version           INT           NOT NULL DEFAULT 1,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 重算时先删后插,同一账单同一表计不会有两行
    UNIQUE KEY uk_allocation_bill_meter (utility_bill_id, meter_id),
    KEY idx_allocation_tenant (tenant_ref_id, period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='公用事业费分摊结果';

-- ---------- 权限点(幂等,沿用 V48/V49 三件套写法) ----------
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, '能耗-公摊测算', 3, 'energy:allocation:calc', 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.perm = 'energy:allocation:calc' AND m.deleted = 0
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r
JOIN sys_menu m ON m.perm = 'energy:allocation:calc' AND m.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
