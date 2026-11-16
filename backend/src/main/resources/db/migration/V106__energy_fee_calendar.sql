-- =====================================================================
-- V106 缴费账期:年月表、单据留存、确认出账冻结
--
-- 1) eng_fee_period.inputs_changed_time:读数 / 总量 / 表计一有改动就记时间,
--    晚于 generated_time 说明通知单是旧的,确认出账前必须重新生成。
-- 2) eng_fee_snapshot:确认出账时把当月电表 / 水表 / 垃圾工作表冻结成 JSON,
--    之后无论表计增减、改区域,历史月份看到的数字都和已出账的通知单一致。
-- 当月三张供应商账单(南网 / 自来水公司 / 垃圾清理供应商)复用统一附件表 sys_file,
-- biz_type = energy_fee_electric / energy_fee_water / energy_fee_garbage,biz_id = 账期 id,不需要新表。
-- 纯新增,不改任何存量数据。
-- =====================================================================

ALTER TABLE eng_fee_period
    ADD COLUMN inputs_changed_time DATETIME NULL
        COMMENT '最近一次改动读数/总量/表计的时间,晚于 generated_time 表示通知单已过期' AFTER generated_time;

CREATE TABLE eng_fee_snapshot (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    fee_period_id BIGINT       NOT NULL,
    kind          VARCHAR(16)  NOT NULL COMMENT 'ELECTRIC / WATER / GARBAGE',
    content       MEDIUMTEXT   NULL COMMENT '确认出账时冻结的工作表 JSON',
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32)  NULL,
    create_time   DATETIME     NULL,
    update_by     VARCHAR(32)  NULL,
    update_time   DATETIME      NULL,
    version       INT          NOT NULL DEFAULT 1,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_fee_snapshot_period (fee_period_id, kind)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='缴费账期确认出账冻结快照';

-- ---------- 单据留存的权限点:附件模块按「业务类型 → 权限前缀」放行 ----------
-- 上传/删除需要 energy:feebill:edit,查看/下载需要 energy:feebill:query(管理员不受限)。
-- 已经拥有 电费 / 水费 / 垃圾处理费 / 缴费汇总 任一页面的角色与用户自动获得,不按角色名批量授权。
INSERT INTO sys_menu (parent_id, name, type, perm, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT COALESCE((SELECT MIN(id) FROM sys_menu WHERE TRIM(BOTH '/' FROM path) = 'energy/summary' AND deleted = 0), 0),
       t.label, 3, t.perm, 0, 0, 1, 1, 'system', NOW(), 1, 0
FROM (SELECT 'energy:feebill:query' AS perm, '缴费单据-查询' AS label
      UNION ALL SELECT 'energy:feebill:edit', '缴费单据-编辑') t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perm = t.perm AND m.deleted = 0);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT g.role_id, perm.id
FROM sys_role_menu g
JOIN sys_menu page ON page.id = g.menu_id AND page.deleted = 0 AND page.type <> 3
    AND TRIM(BOTH '/' FROM page.path) IN ('energy/electric', 'energy/water', 'energy/garbage', 'energy/summary')
JOIN sys_menu perm ON perm.perm IN ('energy:feebill:query', 'energy:feebill:edit') AND perm.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = g.role_id AND e.menu_id = perm.id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT g.user_id, perm.id
FROM sys_user_menu g
JOIN sys_menu page ON page.id = g.menu_id AND page.deleted = 0 AND page.type <> 3
    AND TRIM(BOTH '/' FROM page.path) IN ('energy/electric', 'energy/water', 'energy/garbage', 'energy/summary')
JOIN sys_menu perm ON perm.perm IN ('energy:feebill:query', 'energy:feebill:edit') AND perm.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_menu e WHERE e.user_id = g.user_id AND e.menu_id = perm.id);
