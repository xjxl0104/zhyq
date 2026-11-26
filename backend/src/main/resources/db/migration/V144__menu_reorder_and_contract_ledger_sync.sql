-- V144:
-- 1. 办公管理「任务」改名「任务管理」，缴费管理新增首个子菜单「合同基础台账」(/energy/contracts)
-- 2. 放宽 biz_contract.code 长度至 160 以容纳完整双协议编号，并建立 eng_lease_contract.biz_contract_id 关联
-- 3. 按租户+起止日+面积+单价自动关联历史正式合同，并将合同基础台账中正确的协议编号同步至招商正式合同

-- 1) 菜单改名与新增
UPDATE sys_menu
SET name = '任务管理'
WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'oa/task';

SET @energy_parent_id = (
    SELECT parent_id FROM sys_menu
    WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'energy/deposit'
    ORDER BY id ASC LIMIT 1
);
SET @energy_parent_id = COALESCE(@energy_parent_id, (
    SELECT parent_id FROM sys_menu
    WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'energy/summary'
    ORDER BY id ASC LIMIT 1
), 0);

INSERT INTO sys_menu (parent_id, name, path, perm, icon, type, sort, visible, status, create_by, create_time, version, deleted)
SELECT @energy_parent_id, '合同基础台账', '/energy/contracts', 'energy:contracts:query', 'Document', 2, 1, 1, 1, 'system', NOW(), 1, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'energy/contracts'
);

SET @contracts_menu_id = (
    SELECT id FROM sys_menu WHERE deleted = 0 AND TRIM(BOTH '/' FROM path) = 'energy/contracts' ORDER BY id ASC LIMIT 1
);

-- 已有任一缴费管理页面的角色/用户与 admin 自动获得「合同基础台账」权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT r.id, @contracts_menu_id
FROM sys_role r
WHERE @contracts_menu_id IS NOT NULL AND r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = r.id AND e.menu_id = @contracts_menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT g.role_id, @contracts_menu_id
FROM sys_role_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('energy/deposit', 'energy/rent', 'energy/property', 'energy/electric', 'energy/water', 'energy/garbage', 'energy/summary')
WHERE @contracts_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = g.role_id AND e.menu_id = @contracts_menu_id);

INSERT INTO sys_user_menu (user_id, menu_id)
SELECT DISTINCT g.user_id, @contracts_menu_id
FROM sys_user_menu g
JOIN sys_menu src ON src.id = g.menu_id AND src.deleted = 0 AND src.type <> 3
    AND TRIM(BOTH '/' FROM src.path) IN ('energy/deposit', 'energy/rent', 'energy/property', 'energy/electric', 'energy/water', 'energy/garbage', 'energy/summary')
WHERE @contracts_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_user_menu e WHERE e.user_id = g.user_id AND e.menu_id = @contracts_menu_id);

-- 2) 正式合同编号扩容 + 合同基础台账增加关联外键
ALTER TABLE biz_contract
    MODIFY COLUMN code VARCHAR(160) NOT NULL COMMENT '合同编号(全局唯一)';

ALTER TABLE eng_lease_contract
    ADD COLUMN biz_contract_id BIGINT NULL COMMENT '关联招商租赁正式合同ID(biz_contract.id)' AFTER seq_no,
    ADD KEY idx_lease_contract_biz (biz_contract_id);

-- 3) 历史数据匹配关联: 租户名 + 起止日 + 面积 (+ 单价区分云山同面积同起止日的2F/3F合同)
UPDATE eng_lease_contract lc
SET lc.biz_contract_id = (
    SELECT bc.id
    FROM biz_contract bc
    JOIN biz_tenant bt ON bt.id = bc.tenant_ref_id AND bt.deleted = 0
    WHERE bc.deleted = 0
      AND bc.contract_type = 1
      AND bt.name = lc.tenant_name
      AND bc.start_date = lc.start_date
      AND bc.end_date = lc.end_date
      AND ABS(COALESCE(bc.rent_area, 0) - COALESCE(lc.lease_area, 0)) < 0.05
    ORDER BY ABS(COALESCE(bc.rent_price, 0) - COALESCE(lc.rent_unit_price, 0)) ASC, bc.id ASC
    LIMIT 1
)
WHERE lc.deleted = 0 AND lc.biz_contract_id IS NULL;

-- 4) 将合同基础台账中正确的协议编号同步覆盖到已关联的正式合同编号(跳过空值/待补充/重复编号)
UPDATE biz_contract bc
JOIN eng_lease_contract lc ON lc.biz_contract_id = bc.id AND lc.deleted = 0
LEFT JOIN biz_contract dup ON dup.code = TRIM(lc.agreement_no) AND dup.deleted = 0 AND dup.id <> bc.id
SET bc.code = TRIM(lc.agreement_no)
WHERE bc.deleted = 0
  AND lc.agreement_no IS NOT NULL
  AND TRIM(lc.agreement_no) <> ''
  AND TRIM(lc.agreement_no) <> '待补充'
  AND dup.id IS NULL;
