-- =====================================================================
-- V59 全民营销 · 阶段 C · 云仓 ERP 开放接口(只留接口)
-- 规范:docs/marketing/PARK-MKT-001-开发方案-V1.2.md §2.5 §2.6 §5.3
-- 范围:凭证 / 货主编码映射 / 同步日志 / nonce 防重放(替代 Redis)。
-- 不含:webhook/拉取双适配器、心跳断连、四关验收、对账、云仓结算单 —— 有真实云仓接入时再补。
-- =====================================================================

CREATE TABLE crm_warehouse_erp (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id   BIGINT       NOT NULL,
    mode           TINYINT      NOT NULL DEFAULT 1 COMMENT '1 webhook 推送(唯一已实现) 2 我方拉取 3 文件',
    app_id         VARCHAR(32)  NOT NULL COMMENT 'wh_xxxx,请求头 X-App-Id',
    secret_enc     VARCHAR(255) NOT NULL COMMENT 'HMAC 密钥 AES-GCM 密文,只在签发时明文回显一次',
    env            TINYINT      NOT NULL DEFAULT 1 COMMENT '1 沙箱 2 正式',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    callback_url   VARCHAR(255) NULL COMMENT '预留:园区回推给云仓',
    field_mapping  JSON         NULL COMMENT '预留:拉取模式字段映射',
    last_sync_at   DATETIME     NULL COMMENT '最近一次收到事件',
    fail_count     INT          NOT NULL DEFAULT 0,
    project_id     BIGINT       NULL,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_warehouse_erp (warehouse_id),
    UNIQUE KEY uk_warehouse_erp_app (app_id)
) COMMENT '云仓 ERP 接入凭证';

CREATE TABLE crm_customer_erp_map (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id    BIGINT       NOT NULL,
    warehouse_id   BIGINT       NOT NULL,
    customer_code  VARCHAR(64)  NOT NULL COMMENT '货主在该云仓 ERP 里的编码,订单归属只认它(§2.6 v6 修正)',
    status         TINYINT      NOT NULL DEFAULT 1,
    project_id     BIGINT       NULL,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_erp_map_code (warehouse_id, customer_code),
    UNIQUE KEY uk_erp_map_customer (customer_id, warehouse_id)
) COMMENT '客户 ↔ 云仓货主编码';

CREATE TABLE crm_erp_sync_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id   BIGINT       NULL,
    app_id         VARCHAR(32)  NULL,
    direction      VARCHAR(4)   NOT NULL DEFAULT 'in',
    event          VARCHAR(32)  NULL,
    order_no       VARCHAR(64)  NULL,
    http_status    INT          NOT NULL,
    ok             TINYINT      NOT NULL DEFAULT 0,
    error_code     VARCHAR(32)  NULL COMMENT 'SIGN_INVALID / APP_DISABLED / BAD_PAYLOAD / WH_MISMATCH / NONCE_REPLAY / UNMAPPED …',
    error          VARCHAR(500) NULL,
    payload_digest VARCHAR(64)  NULL COMMENT 'body 的 sha256,排错不落原文',
    referral_order_id BIGINT    NULL,
    project_id     BIGINT       NULL,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_erp_log_wh (warehouse_id, create_time),
    KEY idx_erp_log_order (order_no)
) COMMENT 'ERP 同步日志';

-- nonce 防重放(§5.3:5 分钟内不得重复);没有 Redis,用主键唯一 + 定时清理
CREATE TABLE crm_erp_nonce (
    app_id     VARCHAR(32) NOT NULL,
    nonce      VARCHAR(64) NOT NULL,
    expire_at  DATETIME    NOT NULL,
    PRIMARY KEY (app_id, nonce),
    KEY idx_erp_nonce_expire (expire_at)
) COMMENT 'ERP 请求 nonce(防重放)';

-- 权限点:ERP 对接页(运营 config/audit,管理员全部)
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT 0, t.nm, 3, t.p, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'crm:marketing:erp:query' AS p, '全民营销-ERP对接-查询' AS nm
    UNION ALL SELECT 'crm:marketing:erp:config', '全民营销-ERP对接-签发/重置凭证/映射'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm = t.p AND m.deleted = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r
JOIN sys_menu m ON m.type = 3 AND m.perm IN ('crm:marketing:erp:query', 'crm:marketing:erp:config') AND m.deleted = 0
WHERE r.code IN ('admin', 'mkt_ops') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO route_module_mapping (route, module, module_name, is_core, enabled)
SELECT '/open/v1/erp/**', 'crm', '招商管理', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM route_module_mapping WHERE route = '/open/v1/erp/**');
