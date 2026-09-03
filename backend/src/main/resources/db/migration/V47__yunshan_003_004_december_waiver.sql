-- V47: 云山 -003/-004 补充协议免租口径同改「每年12月免租金及物业费」(2026-09-03 负责人二次拍板)
--
-- V46 当时只改了 -001/-002,并注明 -003/-004 因「首年免租月(2026年12月)与循环免租月重合、
-- 全期少免 1 个月」会与登记表「合同租金总额」对不平,留待负责人拍板。负责人已明确口径:
--
--   免租期 2026-07-01 ~ 2026-11-30 = 补拆迁期(合同原文「搬迁期补助…抵扣20260701-20261130
--   租赁费」,租金由补助抵扣、物业由免缴期覆盖);2026 年 12 月起,每年 12 月租金与物业费
--   全免,以此类推 15 年 —— 与合同「15 年 15 个免租月」对齐。
--
-- 因此 Dec2026 ~ Dec2040 共 15 个免租月(YEARLY_DECEMBER 规则不设生效起点,覆盖整个合同期,
-- 合同 2026-07-01~2041-06-30 内正好 15 个自然年 12 月)。原「合同年度末月(6月)」口径作废。
--
-- ⚠️ 已知且经负责人确认接受的差异:改后全期租金合计比登记表「合同租金总额」高出
--    约 105,600 元(-003) / 162,000 元(-004),正好一个月租金 —— 因为该字段是按旧口径
--    (首年免 2026-12 + 2027 起每年 6 月免 15 次 = 16 个免租月)算出来的,新口径是 15 个。
--    负责人口径以合同为准,「合同租金总额」字段本身未同步更正,后续如需更正另行处理。
--
-- 改法同 V46:只改「免租期限」(free_period_raw);「优惠期/备注」(discount_raw) 是导入的
--    合同原文,保留不动。原文里「免租期计算:2026年12月1日至2026年12月31日」与循环免租的
--    第一个 12 月重合,重复免租无副作用(两者都让该月为 0)。
--
-- 账单同步:部署后 ReceivableBillSyncJob 幂等重算,未收款账单自动改金额,已收款账单不动。
-- 安全: 改前整行备份到 bak_yunshan_dec_fix(V46 已建,按 id 防重、重跑不重复);命中行数记入
--       bak_yunshan_dec_fix_audit(先 count 再 update,Flyway 逐句 ROW_COUNT() 不可靠);
--       UPDATE 带防重 WHERE,可重复执行。
-- 恢复: UPDATE fin_receivable_register r JOIN bak_yunshan_dec_fix b ON b.id=r.id
--          SET r.free_period_raw = b.free_period_raw;
-- 注意: 备份 INSERT 走显式列清单(生成列 active_unique_key 排除),同 V43/V45/V46。

-- ---------- 0) 备份(表由 V46 建;此处仅补 -003/-004 两行) ----------
CREATE TABLE IF NOT EXISTS bak_yunshan_dec_fix LIKE fin_receivable_register;
CREATE TABLE IF NOT EXISTS bak_yunshan_dec_fix_audit (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    step         VARCHAR(64) NOT NULL,
    rows_matched INT         NOT NULL,
    executed_at  DATETIME(3) NOT NULL
) COMMENT 'V46/V47 云山12月免租口径修正行数留痕';

INSERT INTO bak_yunshan_dec_fix
       (id,internal_code,business_key,seq_no,agreement_no_raw,tenant_name_raw,space_name_raw,
        charge_area,actual_area,shared_area,contract_term_raw,contract_rent_total,contract_period_raw,
        contract_start_date,contract_end_date,escalation_raw,free_term_raw,free_period_raw,discount_raw,
        rent_rate_raw,property_rate_raw,monthly_rent,monthly_property,monthly_total,rent_deposit,
        property_deposit,collection_timing_raw,first_collection_raw,rent_account_id,property_account_id,
        rent_account_masked,property_account_masked,notes_raw,deposit_difference,late_fee_start_date,
        tenant_ref_id,space_id,room_id,contract_id,status,source_batch_id,source_row_id,source_version,
        tenant_id,create_by,create_time,update_by,update_time,version,deleted)
SELECT  r.id,r.internal_code,r.business_key,r.seq_no,r.agreement_no_raw,r.tenant_name_raw,r.space_name_raw,
        r.charge_area,r.actual_area,r.shared_area,r.contract_term_raw,r.contract_rent_total,r.contract_period_raw,
        r.contract_start_date,r.contract_end_date,r.escalation_raw,r.free_term_raw,r.free_period_raw,r.discount_raw,
        r.rent_rate_raw,r.property_rate_raw,r.monthly_rent,r.monthly_property,r.monthly_total,r.rent_deposit,
        r.property_deposit,r.collection_timing_raw,r.first_collection_raw,r.rent_account_id,r.property_account_id,
        r.rent_account_masked,r.property_account_masked,r.notes_raw,r.deposit_difference,r.late_fee_start_date,
        r.tenant_ref_id,r.space_id,r.room_id,r.contract_id,r.status,r.source_batch_id,r.source_row_id,r.source_version,
        r.tenant_id,r.create_by,r.create_time,r.update_by,r.update_time,r.version,r.deleted
  FROM fin_receivable_register r
  LEFT JOIN bak_yunshan_dec_fix k ON k.id = r.id
 WHERE k.id IS NULL
   AND r.tenant_name_raw LIKE '%云山%'
   AND (r.agreement_no_raw LIKE '%20260519-003%' OR r.agreement_no_raw LIKE '%20260519-004%');

-- ---------- 1) 免租期限改为每年12月口径 ----------
INSERT INTO bak_yunshan_dec_fix_audit (step, rows_matched, executed_at)
SELECT '云山003/004:免租期限改每年12月', COUNT(*), NOW(3) FROM fin_receivable_register
 WHERE tenant_name_raw LIKE '%云山%'
   AND (agreement_no_raw LIKE '%20260519-003%' OR agreement_no_raw LIKE '%20260519-004%')
   AND (free_period_raw IS NULL OR free_period_raw NOT LIKE '%每年12月免%');
UPDATE fin_receivable_register
   SET free_period_raw = '每年12月免租金及物业费'
 WHERE tenant_name_raw LIKE '%云山%'
   AND (agreement_no_raw LIKE '%20260519-003%' OR agreement_no_raw LIKE '%20260519-004%')
   AND (free_period_raw IS NULL OR free_period_raw NOT LIKE '%每年12月免%');
