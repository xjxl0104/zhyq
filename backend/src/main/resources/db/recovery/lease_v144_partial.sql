-- Recovery for V144 after MySQL 8 collation mismatch (utf8mb4_0900_ai_ci vs utf8mb4_general_ci)
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
WHERE lc.deleted = 0 AND lc.biz_contract_id IS NULL;

UPDATE biz_contract bc
JOIN eng_lease_contract lc ON lc.biz_contract_id = bc.id AND lc.deleted = 0
JOIN (
    SELECT TRIM(agreement_no) AS ag_no, MIN(id) AS min_id
    FROM eng_lease_contract
    WHERE deleted = 0
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
SET bc.code = TRIM(lc.agreement_no)
WHERE bc.deleted = 0
  AND dup.id IS NULL;
