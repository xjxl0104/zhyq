-- V145:
-- 1. 清理此前双向同步误引入到 eng_lease_contract(合同基础台账) 的冗余行，完全保留原始合同基础台账数据
-- 2. 清理此前因日期/面积微小差异误新建到 biz_contract(正式合同) 的重复行(source='合同基础台账同步')
-- 3. 按「精确匹配 -> 同租户未关联最近匹配」两阶段把原始合同基础台账与正式合同1对1关联，并以合同基础台账的协议编号和条款为准完善正式合同

-- 1) 清理误插入到合同基础台账的行
UPDATE eng_lease_contract
SET deleted = 1, update_time = NOW()
WHERE deleted = 0
  AND biz_contract_id IS NOT NULL
  AND total_months IS NULL
  AND pricing_mode IS NULL
  AND collection_timing IS NULL
  AND deposit_terms IS NULL
  AND rules_json IS NULL
  AND COALESCE(rent_deposit_paid, 0) = 0
  AND COALESCE(property_deposit_paid, 0) = 0;

-- 2) 解除指向重复补建正式合同的引用，并软删重复补建的正式合同行
UPDATE eng_lease_contract lc
JOIN biz_contract bc ON bc.id = lc.biz_contract_id
SET lc.biz_contract_id = NULL
WHERE bc.source COLLATE utf8mb4_general_ci = '合同基础台账同步';

UPDATE biz_contract
SET deleted = 1, update_time = NOW()
WHERE deleted = 0
  AND source COLLATE utf8mb4_general_ci = '合同基础台账同步';

-- 3) Pass 1: 精确匹配(同租户 + 起止日 + 面积 + 单价最接近)
UPDATE eng_lease_contract lc
SET lc.biz_contract_id = (
    SELECT bc.id
    FROM biz_contract bc
    JOIN biz_tenant bt ON bt.id = bc.tenant_ref_id AND bt.deleted = 0
    WHERE bc.deleted = 0
      AND bc.contract_type = 1
      AND bt.name COLLATE utf8mb4_general_ci = lc.tenant_name COLLATE utf8mb4_general_ci
      AND bc.start_date = lc.start_date
      AND bc.end_date = lc.end_date
      AND ABS(COALESCE(bc.rent_area, 0) - COALESCE(lc.lease_area, 0)) < 0.05
    ORDER BY ABS(COALESCE(bc.rent_price, 0) - COALESCE(lc.rent_unit_price, 0)) ASC, bc.id ASC
    LIMIT 1
)
WHERE lc.deleted = 0 AND lc.status = 1 AND lc.biz_contract_id IS NULL;

-- 4) Pass 2: 宽松匹配(同租户下尚未被其他基础台账占用的正式合同，解决历史正式合同起止日/面积微小差异)
CREATE TEMPORARY TABLE tmp_linked_biz AS
SELECT DISTINCT biz_contract_id AS bid FROM eng_lease_contract WHERE deleted = 0 AND biz_contract_id IS NOT NULL;

UPDATE eng_lease_contract lc
SET lc.biz_contract_id = (
    SELECT bc.id
    FROM biz_contract bc
    JOIN biz_tenant bt ON bt.id = bc.tenant_ref_id AND bt.deleted = 0
    LEFT JOIN tmp_linked_biz lb ON lb.bid = bc.id
    WHERE bc.deleted = 0
      AND bc.contract_type = 1
      AND lb.bid IS NULL
      AND bt.name COLLATE utf8mb4_general_ci = lc.tenant_name COLLATE utf8mb4_general_ci
    ORDER BY ABS(COALESCE(bc.rent_area, 0) - COALESCE(lc.lease_area, 0)) ASC,
             ABS(COALESCE(bc.rent_price, 0) - COALESCE(lc.rent_unit_price, 0)) ASC,
             bc.id ASC
    LIMIT 1
)
WHERE lc.deleted = 0 AND lc.status = 1 AND lc.biz_contract_id IS NULL;

DROP TEMPORARY TABLE tmp_linked_biz;

-- 5) 以合同基础台账为准，同步完善已关联正式合同的编号、区域、面积、起止日、单价、保证金、递增率
UPDATE biz_contract bc
JOIN eng_lease_contract lc ON lc.biz_contract_id = bc.id AND lc.deleted = 0 AND lc.status = 1
SET bc.lease_scope = lc.unit_name,
    bc.start_date = lc.start_date,
    bc.end_date = lc.end_date,
    bc.rent_area = lc.lease_area,
    bc.rent_price = ROUND(
        CASE WHEN COALESCE(lc.fixed_monthly_rent, 0) > 0 AND COALESCE(lc.lease_area, 0) > 0
             THEN lc.fixed_monthly_rent / lc.lease_area
             ELSE COALESCE(lc.rent_unit_price, 0) END, 2),
    bc.property_price = ROUND(COALESCE(lc.property_unit_price, 2), 2),
    bc.deposit = ROUND(COALESCE(lc.rent_deposit, 0) + COALESCE(lc.property_deposit, 0), 2),
    bc.increase_rate = ROUND(COALESCE(lc.escalation_rate, 0) * 100, 2),
    bc.update_time = NOW()
WHERE bc.deleted = 0;

UPDATE biz_contract bc
JOIN eng_lease_contract lc ON lc.biz_contract_id = bc.id AND lc.deleted = 0 AND lc.status = 1
JOIN (
    SELECT TRIM(agreement_no) AS ag_no, MIN(id) AS min_id
    FROM eng_lease_contract
    WHERE deleted = 0 AND status = 1
      AND agreement_no IS NOT NULL
      AND TRIM(agreement_no) <> ''
      AND TRIM(agreement_no) <> '待补充'
    GROUP BY TRIM(agreement_no)
    HAVING COUNT(*) = 1
) uniq ON uniq.min_id = lc.id
LEFT JOIN biz_contract dup
       ON dup.code COLLATE utf8mb4_general_ci = TRIM(lc.agreement_no) COLLATE utf8mb4_general_ci
      AND dup.deleted = 0
      AND dup.id <> bc.id
SET bc.code = TRIM(lc.agreement_no),
    bc.update_time = NOW()
WHERE bc.deleted = 0
  AND dup.id IS NULL;
