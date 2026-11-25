-- =====================================================================
-- V131 全民营销 · 内部专属公海池、三道闸防撞洗数日志与30秒算价留资扩展
-- 设计约束：
--   1. 采用 customer_id 1:1 独立扩展表 crm_public_sea_meta，零 ALTER 存量大表 crm_customer，零锁表风险。
--   2. 严格限制仅园区内部招商专员(mkt_sales)/运营(mkt_ops)/超管(admin)及加盟云仓主账号可访问，不对外部 P1-P4 开放。
--   3. 统一 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci。
-- =====================================================================

CREATE TABLE IF NOT EXISTS crm_public_sea_meta (
    customer_id        BIGINT        NOT NULL PRIMARY KEY COMMENT '关联 crm_customer.id',
    tenant_id          BIGINT        NOT NULL DEFAULT 1,
    project_id         BIGINT        NULL COMMENT '所属园区项目ID',
    sea_source         VARCHAR(32)   NOT NULL DEFAULT 'EXPIRE_RETURN' COMMENT '来源:CALC_HOT/CHANMAMA/TUNGEE_JOB/EXPIRE_RETURN/PARK_CRM/MANUAL_IMPORT',
    shop_name          VARCHAR(120)  NULL COMMENT '关联电商店铺名',
    category           VARCHAR(64)   NULL COMMENT '主营发货类目',
    est_daily_orders   INT           NULL COMMENT '预估日均发货单量(单/天)',
    avg_weight_kg      DECIMAL(10,2) NULL COMMENT '包裹平均重量(kg)',
    calc_low_price     DECIMAL(10,2) NULL COMMENT '30秒算价器测算一口价下限(元/单)',
    calc_high_price    DECIMAL(10,2) NULL COMMENT '30秒算价器测算一口价上限(元/单)',
    logistics_score    DECIMAL(4,2)  NULL COMMENT '店铺物流体验分(如4.42)',
    urgency_level      VARCHAR(16)   NOT NULL DEFAULT 'B' COMMENT '换仓急迫度评级:S/A/B',
    urgency_reason     VARCHAR(500)  NULL COMMENT '换仓痛点评级判据说明',
    block_note         VARCHAR(500)  NULL COMMENT '上轮未成卡点或核心诉求',
    claim_status       VARCHAR(24)   NOT NULL DEFAULT 'UNCLAIMED' COMMENT 'UNCLAIMED/CLAIMED_ADMIN/FEED_WH/CLAIMED_WH/ARCHIVED_COLD',
    claim_holder_type  VARCHAR(16)   NULL COMMENT '持有人类型:ADMIN/WAREHOUSE',
    claim_holder_id    BIGINT        NULL COMMENT '持有人ID(sys_user.id 或 crm_warehouse.id)',
    claim_holder_name  VARCHAR(64)   NULL COMMENT '持有人或云仓展示名称',
    claimed_at         DATETIME      NULL COMMENT '最近认领或投喂时间',
    sla_deadline_at    DATETIME      NULL COMMENT '首触达保活截止时间(默认认领后+72h)',
    first_touched_at   DATETIME      NULL COMMENT '完成首通核实纪要时间(非空即解除超时弹回)',
    first_touch_note   VARCHAR(500)  NULL COMMENT '首通核实纪要内容',
    feed_warehouse_ids VARCHAR(255)  NULL COMMENT '定向开放认领的云仓ID逗号分隔(空表示园区全部匹配上线云仓)',
    release_count      INT           NOT NULL DEFAULT 0 COMMENT '累计超时弹回或弃单次数(满3次入冷宫)',
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_sea_scope_status (tenant_id, project_id, claim_status, sea_source),
    KEY idx_sea_sla (claim_status, first_touched_at, sla_deadline_at),
    KEY idx_sea_holder (claim_holder_type, claim_holder_id, claimed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内部专属公海池客户画像与72h认领保活状态表';

CREATE TABLE IF NOT EXISTS crm_public_sea_wash_batch (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL DEFAULT 1,
    project_id           BIGINT       NULL COMMENT '所属园区项目ID',
    batch_no             VARCHAR(48)  NOT NULL COMMENT '洗数批次号',
    sea_source           VARCHAR(32)  NOT NULL COMMENT '数据来源编码',
    file_name            VARCHAR(160) NULL COMMENT '导入文件名或入口标识',
    total_rows           INT          NOT NULL DEFAULT 0 COMMENT '原始提交总行数',
    inserted_rows        INT          NOT NULL DEFAULT 0 COMMENT '净入公海池户数',
    blocked_private_lock INT          NOT NULL DEFAULT 0 COMMENT '撞外部伙伴90天在锁私海拦截数',
    blocked_wh_hmac      INT          NOT NULL DEFAULT 0 COMMENT '撞加盟云仓AES+HMAC保密老客拦截数',
    blocked_dup_agency   INT          NOT NULL DEFAULT 0 COMMENT '代理记账高频重号或冷宫死单过滤数',
    blocked_details_json JSON         NULL COMMENT '拦截回执抽样明细(不含保密老客明文)',
    operator             VARCHAR(64)  NOT NULL DEFAULT 'system',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_wash_scope_time (tenant_id, project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公海入池三道防撞洗数批次审计表';

-- 幂等回填历史已处于公海(无推荐人且跟进中)的存量云仓线客户，激活存量死水
INSERT INTO crm_public_sea_meta (customer_id, tenant_id, project_id, sea_source, category, urgency_level, urgency_reason, block_note, claim_status)
SELECT c.id, c.tenant_id, c.project_id, 'EXPIRE_RETURN', COALESCE(NULLIF(c.industry, ''), '综合电商'),
       'B', '历史公海存量客户自动归集激活，待内部招商首通核实',
       COALESCE(NULLIF(c.intake_remark, ''), NULLIF(c.demand_area, ''), '待电联核实日均单量与换仓窗口'),
       'UNCLAIMED'
FROM crm_customer c
WHERE c.deleted = 0
  AND c.referrer_id IS NULL
  AND (c.status IS NULL OR c.status = 1)
  AND (c.biz_line IS NULL OR c.biz_line = 2)
  AND NOT EXISTS (SELECT 1 FROM crm_public_sea_meta m WHERE m.customer_id = c.id);

-- 规则参数幂等预置：72小时首触达保活、云仓每日限领5户、云仓在途上限10户
INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted)
SELECT 'marketing', t.k, t.v, t.r, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'sea_sla_hours'     AS k, '72' AS v, '公海认领后首触达保活时限（小时，超时未填核实纪要自动弹回公海）' AS r
    UNION ALL SELECT 'sea_wh_daily_cap',  '5',  '加盟云仓每日最多认领公海热单户数'
    UNION ALL SELECT 'sea_wh_active_cap', '10', '加盟云仓在途未签约公海客户上限户数'
) t
WHERE NOT EXISTS (SELECT 1 FROM biz_setting b WHERE b.module = 'marketing' AND b.skey = t.k AND b.deleted = 0);

-- 注册后台菜单「公海线索池」与细粒度权限点（挂在全民营销/招商目录下，授权 admin / mkt_sales / mkt_ops）
INSERT INTO sys_menu (parent_id, name, type, path, component, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT COALESCE((SELECT parent_id FROM sys_menu WHERE path = '/crm/marketing/customer' AND deleted = 0 ORDER BY id LIMIT 1), 0),
       '公海线索池', 2, '/crm/marketing/public-sea', 'crm/marketing/PublicSea', 16, 1, 1, 1, 'system', NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/crm/marketing/public-sea' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, t.nm, 3, t.p, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'crm:marketing:public-sea:query' AS p, '全民营销-内部公海池-查询与下钻' AS nm
    UNION ALL SELECT 'crm:marketing:public-sea:edit', '全民营销-内部公海池-洗数导入/认领/投喂云仓'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm = t.p AND m.deleted = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND (m.path = '/crm/marketing/public-sea' OR m.perm IN ('crm:marketing:public-sea:query', 'crm:marketing:public-sea:edit'))
WHERE r.deleted = 0 AND r.code IN ('admin', 'mkt_sales', 'mkt_ops')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
