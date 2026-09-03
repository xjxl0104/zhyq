-- V46: 云山 -001/-002 补充协议免租口径改为「每年12月免租金及物业费」(2026-09-03 负责人拍板)
--
-- 背景:这两份协议原「免租期限」写「每年最后一个月免租一个月」,系统曾按「合同年度末月」
--      理解(6 月起租→免次年 5 月)。负责人口径:「每年最后一个月」= 自然年 12 月,
--      且 12 月租金与物业费全免;合同 15 年 = 15 个 12 月。
--
-- 为什么原来的「合同租金总额对平」验不出这个差异:递增每 3 年一档,对 6 月起租的合同,
-- 每个自然年 12 月与其后的次年 5 月落在同一档位、单月金额相同,15 个免租月一一对应,
-- 两种口径全期总额完全相等(实算 -001 差 +0.12 元、-002 差 -0.07 元,均 < 1 元)。
-- 故口径只能由条款措辞显式指定,不能靠总额反推。
--
-- 只改 -001/-002(负责人点名的两份,均 2026-06-01 起租)。**-003/-004 不动**:
-- 它们 2026-07-01 起租、首年免租月本就是 2026 年 12 月,若也改成「每年12月」,
-- 首年免租月与循环免租月重合 → 全期少免 1 个月,总额将比合同租金总额多出
-- 约 105,600(-003) / 162,000(-004) 元,对不平。需要改的话得负责人另行拍板。
--
-- 改法:只改「免租期限」(free_period_raw) 这个表达免租口径的字段;
--      「优惠期/备注」(discount_raw) 是导入的合同原文,保留不动。
--      代码侧 ReceivableCalculator 让「每年12月免」优先于「每年最后一个月」,
--      两种措辞并存时不会双重免租。
--
-- 账单同步:部署后 ReceivableBillSyncJob 幂等重算,未收款账单自动改金额,已收款账单不动。
-- 安全: 改前整行备份到 bak_yunshan_dec_fix(按 id 防重,重跑不重复);命中行数记入
--       bak_yunshan_dec_fix_audit(先 count 再 update,Flyway 逐句 ROW_COUNT() 不可靠);
--       UPDATE 带防重 WHERE,可重复执行。
-- 恢复: UPDATE fin_receivable_register r JOIN bak_yunshan_dec_fix b ON b.id=r.id
--          SET r.free_period_raw = b.free_period_raw;
-- 注意: fin_receivable_register 有 STORED GENERATED 列 active_unique_key,
--       备份 INSERT 走显式列清单(V44 时点全部非生成列),同 V43/V45 的处理。

-- ---------- 0) 备份与留痕表 ----------
CREATE TABLE IF NOT EXISTS bak_yunshan_dec_fix LIKE fin_receivable_register;
CREATE TABLE IF NOT EXISTS bak_yunshan_dec_fix_audit (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    step         VARCHAR(64) NOT NULL,
    rows_matched INT         NOT NULL,
    executed_at  DATETIME(3) NOT NULL
) COMMENT 'V46 云山12月免租口径修正行数留痕';

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
   AND (r.agreement_no_raw LIKE '%20260519-001%' OR r.agreement_no_raw LIKE '%20260519-002%');

-- ---------- 1) 免租期限改为每年12月口径 ----------
INSERT INTO bak_yunshan_dec_fix_audit (step, rows_matched, executed_at)
SELECT '云山001/002:免租期限改每年12月', COUNT(*), NOW(3) FROM fin_receivable_register
 WHERE tenant_name_raw LIKE '%云山%'
   AND (agreement_no_raw LIKE '%20260519-001%' OR agreement_no_raw LIKE '%20260519-002%')
   AND (free_period_raw IS NULL OR free_period_raw NOT LIKE '%每年12月免%');
UPDATE fin_receivable_register
   SET free_period_raw = '每年12月免租金及物业费'
 WHERE tenant_name_raw LIKE '%云山%'
   AND (agreement_no_raw LIKE '%20260519-001%' OR agreement_no_raw LIKE '%20260519-002%')
   AND (free_period_raw IS NULL OR free_period_raw NOT LIKE '%每年12月免%');
