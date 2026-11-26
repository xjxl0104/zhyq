-- =====================================================================
-- V137:
--   1. 菜单重组与下线:
--      - 资产管理更名「物资管理」,归属园区运营下第一位
--      - 空间与资产更名「项目概况」,隐藏「建筑」中间目录(项目/建筑/租控直属)
--      - 能源与物联更名「智慧物联」,取消冗余内层目录,下线智能硬件/门禁/门锁/
--        充电桩/传感器/空开/点位/通道/厂商及整个便捷通行(12项)
--      - 缴费管理新增「保证金」(/energy/deposit)并放第一位,自动授予已有缴费权限角色/用户
--   2. eng_lease_contract 新增月固定总价(fixed_monthly_rent)及保证金缴纳状态/金额/日期/备注字段
--   3. eng_fee_notice 与 eng_lease_fee_notice 新增开票状态(invoice_status)
-- =====================================================================

-- ---------- 1. 菜单更名与下线 ----------
UPDATE sys_menu SET name = '物资管理'
WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'property/asset';

UPDATE sys_menu SET name = '项目概况'
WHERE deleted = 0 AND type = 1 AND name IN ('空间与资产', '空间管理') AND (path IS NULL OR path = '');

UPDATE sys_menu SET visible = 0, status = 0
WHERE deleted = 0 AND type = 1 AND name = '建筑' AND (path IS NULL OR path = '');

UPDATE sys_menu SET name = '智慧物联'
WHERE deleted = 0 AND type = 1 AND name = '能源与物联' AND (path IS NULL OR path = '');

UPDATE sys_menu SET visible = 0, status = 0
WHERE deleted = 0 AND (
    TRIM(BOTH '/' FROM path) IN (
        'iot/device', 'iot/access', 'iot/lock', 'iot/charging',
        'iot/sensor', 'iot/breaker', 'iot/point', 'iot/channel', 'iot/vendor',
        'acc/access', 'acc/visitor', 'acc/parking'
    )
    OR perm LIKE 'iot:point:%'
    OR perm LIKE 'iot:channel:%'
    OR perm LIKE 'iot:vendor:%'
    OR perm LIKE 'acc:%'
);

UPDATE sys_menu SET visible = 0, status = 0
WHERE deleted = 0 AND type = 1 AND name = '便捷通行' AND (path IS NULL OR path = '');

-- ---------- 2. 缴费管理新增「保证金」(/energy/deposit) ----------
SET @fee_parent = COALESCE(
    (SELECT id FROM sys_menu WHERE deleted = 0 AND type = 1 AND name = '缴费管理' ORDER BY id LIMIT 1),
    (SELECT parent_id FROM sys_menu WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'energy/rent' ORDER BY id LIMIT 1),
    0
);

INSERT INTO sys_menu (parent_id, name, type, path, sort, visible, status, tenant_id, create_by, create_time, version, deleted)
SELECT @fee_parent, '保证金', 2, '/energy/deposit', 18, 1, 1, 1, 'system', NOW(), 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE TRIM(BOTH '/' FROM path) = 'energy/deposit' AND deleted = 0
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT g.role_id, dep.id
FROM sys_role_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('energy/rent', 'energy/property', 'energy/electric', 'energy/water', 'energy/garbage', 'energy/summary')
JOIN sys_menu dep ON TRIM(BOTH '/' FROM dep.path) = 'energy/deposit' AND dep.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu e WHERE e.role_id = g.role_id AND e.menu_id = dep.id
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT r.id, dep.id
FROM sys_role r
JOIN sys_menu dep ON TRIM(BOTH '/' FROM dep.path) = 'energy/deposit' AND dep.deleted = 0
WHERE r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = r.id AND e.menu_id = dep.id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT g.user_id, dep.id
FROM sys_user_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('energy/rent', 'energy/property', 'energy/electric', 'energy/water', 'energy/garbage', 'energy/summary')
JOIN sys_menu dep ON TRIM(BOTH '/' FROM dep.path) = 'energy/deposit' AND dep.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_menu e WHERE e.user_id = g.user_id AND e.menu_id = dep.id
);

-- ---------- 3. 计费合同增加月固定总价与保证金缴纳登记字段 ----------
ALTER TABLE eng_lease_contract
    ADD COLUMN fixed_monthly_rent        DECIMAL(14,2) NULL COMMENT '月固定总价(元/月,填则起始与递增均按此计算)' AFTER rent_unit_price,
    ADD COLUMN rent_deposit_status       TINYINT       NOT NULL DEFAULT 0 COMMENT '租金保证金状态:0未缴纳 1部分缴纳 2已缴纳' AFTER deposit_terms,
    ADD COLUMN rent_deposit_paid         DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '租金保证金已缴金额(元)' AFTER rent_deposit_status,
    ADD COLUMN rent_deposit_paid_date    DATE          NULL COMMENT '租金保证金缴纳日期' AFTER rent_deposit_paid,
    ADD COLUMN property_deposit_status   TINYINT       NOT NULL DEFAULT 0 COMMENT '物业保证金状态:0未缴纳 1部分缴纳 2已缴纳' AFTER rent_deposit_paid_date,
    ADD COLUMN property_deposit_paid     DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '物业保证金已缴金额(元)' AFTER property_deposit_status,
    ADD COLUMN property_deposit_paid_date DATE         NULL COMMENT '物业保证金缴纳日期' AFTER property_deposit_paid,
    ADD COLUMN deposit_remark            VARCHAR(500)  NULL COMMENT '保证金缴纳备注' AFTER property_deposit_paid_date;

-- ---------- 4. 缴费通知单增加开票登记状态 ----------
ALTER TABLE eng_fee_notice
    ADD COLUMN invoice_status TINYINT NOT NULL DEFAULT 0 COMMENT '开票登记:0未开票 1已开票' AFTER payment_status;

ALTER TABLE eng_lease_fee_notice
    ADD COLUMN invoice_status TINYINT NOT NULL DEFAULT 0 COMMENT '开票登记:0未开票 1已开票' AFTER payment_status;
