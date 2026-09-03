-- V45: 应收登记基础资料修正(2026-09-03 负责人口径,四户:李万能/昌泰/微新/中印)
--   1) 李万能 月物业费 9447 → 9400(原登记有误),月租金物业总计 65847 → 65800
--   2) 李万能 滞纳金 2026-10-01 起算(之前逾期暂不收)
--   3) 昌泰   拆迁补助抵扣期 20260801-20261031 物业费同免(优惠备注补免物业条款)
--   4) 微新   拆迁补助抵扣期 20260901-20261130 物业费同免(同上)
--   5) 中印   免租期限 20260601-20260730 → 20260601-20260731(7 月整月免租金及物业费),
--             开始收取租金时间 = 20260801
-- 「免租期限内物业费同免」是代码口径(ReceivableCalculator),不需要动数据;
-- 拆迁抵扣期不属于免租期限,故用优惠备注里的「免物业管理费」条款表达,推断引擎原生支持。
-- 账单同步:部署后 ReceivableBillSyncJob 幂等重算,未收款账单自动改金额,已收款账单不动。
-- 安全: 改前整行备份到 bak_recv_prop_fix(按 id 防重,重跑不重复);每步先把预计命中
--       行数记入 bak_recv_prop_fix_audit 再 UPDATE(Flyway 逐语句执行下 ROW_COUNT()
--       不可靠,实测记成 -1);所有 UPDATE 带旧值/防重 WHERE,可重复执行。
-- 恢复: 按 bak_recv_prop_fix 中的旧值逐列 UPDATE 回写(该表保留修正前完整行)。
-- 本地/新环境登记表为空时全部命中 0 行,为空操作。
-- 注意: fin_receivable_register 有 STORED GENERATED 列 active_unique_key,LIKE 建的
--       备份表同样生成它,备份 INSERT 必须走显式列清单(V44 时点 schema 的全部非生成列,
--       两侧同列表),生成列由备份表自行计算 —— 同 V43 备份 fin_bill 的处理。

-- ---------- 0) 备份与留痕表 ----------
CREATE TABLE IF NOT EXISTS bak_recv_prop_fix LIKE fin_receivable_register;
CREATE TABLE IF NOT EXISTS bak_recv_prop_fix_audit (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    step         VARCHAR(64) NOT NULL,
    rows_matched INT         NOT NULL,
    executed_at  DATETIME(3) NOT NULL
) COMMENT 'V45 应收登记修正行数留痕';

-- bak 表建自 V44 之后的 schema,已含 late_fee_start_date
INSERT INTO bak_recv_prop_fix
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
  LEFT JOIN bak_recv_prop_fix k ON k.id = r.id
 WHERE k.id IS NULL
   AND (r.tenant_name_raw LIKE '%李万能%'
     OR r.tenant_name_raw LIKE '%昌泰供应链%'
     OR r.tenant_name_raw LIKE '%微新供应链%'
     OR r.tenant_name_raw LIKE '%中印国际供应链%');

-- ---------- 1) 李万能 月物业费 9447 → 9400 ----------
INSERT INTO bak_recv_prop_fix_audit (step, rows_matched, executed_at)
SELECT '李万能:月物业费9447→9400', COUNT(*), NOW(3) FROM fin_receivable_register
 WHERE tenant_name_raw LIKE '%李万能%'
   AND monthly_property = 9447.00;
UPDATE fin_receivable_register
   SET monthly_total    = monthly_rent + 9400.00,
       monthly_property = 9400.00
 WHERE tenant_name_raw LIKE '%李万能%'
   AND monthly_property = 9447.00;

-- ---------- 2) 李万能 滞纳金 2026-10-01 起算 ----------
INSERT INTO bak_recv_prop_fix_audit (step, rows_matched, executed_at)
SELECT '李万能:滞纳金2026-10-01起算', COUNT(*), NOW(3) FROM fin_receivable_register
 WHERE tenant_name_raw LIKE '%李万能%'
   AND late_fee_start_date IS NULL;
UPDATE fin_receivable_register
   SET late_fee_start_date = '2026-10-01'
 WHERE tenant_name_raw LIKE '%李万能%'
   AND late_fee_start_date IS NULL;

-- ---------- 3) 昌泰 拆迁抵扣期物业费同免 ----------
INSERT INTO bak_recv_prop_fix_audit (step, rows_matched, executed_at)
SELECT '昌泰:20260801-20261031免物业', COUNT(*), NOW(3) FROM fin_receivable_register
 WHERE tenant_name_raw LIKE '%昌泰供应链%'
   AND (discount_raw IS NULL OR discount_raw NOT LIKE '%20260801-20261031免物业管理费%');
UPDATE fin_receivable_register
   SET discount_raw = CONCAT(IFNULL(discount_raw, ''), '；20260801-20261031免物业管理费')
 WHERE tenant_name_raw LIKE '%昌泰供应链%'
   AND (discount_raw IS NULL OR discount_raw NOT LIKE '%20260801-20261031免物业管理费%');

-- ---------- 4) 微新 拆迁抵扣期物业费同免 ----------
INSERT INTO bak_recv_prop_fix_audit (step, rows_matched, executed_at)
SELECT '微新:20260901-20261130免物业', COUNT(*), NOW(3) FROM fin_receivable_register
 WHERE tenant_name_raw LIKE '%微新供应链%'
   AND (discount_raw IS NULL OR discount_raw NOT LIKE '%20260901-20261130免物业管理费%');
UPDATE fin_receivable_register
   SET discount_raw = CONCAT(IFNULL(discount_raw, ''), '；20260901-20261130免物业管理费')
 WHERE tenant_name_raw LIKE '%微新供应链%'
   AND (discount_raw IS NULL OR discount_raw NOT LIKE '%20260901-20261130免物业管理费%');

-- ---------- 5) 中印 免租期限到 7 月末 + 开始收租 20260801 ----------
INSERT INTO bak_recv_prop_fix_audit (step, rows_matched, executed_at)
SELECT '中印:免租期限至20260731', COUNT(*), NOW(3) FROM fin_receivable_register
 WHERE tenant_name_raw LIKE '%中印国际供应链%'
   AND free_period_raw LIKE '%20260601-20260730%';
UPDATE fin_receivable_register
   SET free_period_raw = REPLACE(free_period_raw, '20260601-20260730', '20260601-20260731')
 WHERE tenant_name_raw LIKE '%中印国际供应链%'
   AND free_period_raw LIKE '%20260601-20260730%';

INSERT INTO bak_recv_prop_fix_audit (step, rows_matched, executed_at)
SELECT '中印:开始收租20260801', COUNT(*), NOW(3) FROM fin_receivable_register
 WHERE tenant_name_raw LIKE '%中印国际供应链%'
   AND (first_collection_raw IS NULL OR first_collection_raw NOT LIKE '%20260801%');
UPDATE fin_receivable_register
   SET first_collection_raw = '20260801开始收取租金'
 WHERE tenant_name_raw LIKE '%中印国际供应链%'
   AND (first_collection_raw IS NULL OR first_collection_raw NOT LIKE '%20260801%');
