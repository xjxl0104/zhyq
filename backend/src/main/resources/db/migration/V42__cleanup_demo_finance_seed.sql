-- V42: 清除财务演示种子数据(负责人 2026-09-01 拍板:金额与对方租客一律以应收明细登记表为准,
--      V8 的 6 条演示账单及其派生数据全部清除)
-- 范围: V8__seed.sql 种的 6 条 fin_bill(code ZD2026*)、V12 种的演示收据/收据日志/收款通知,
--      以及任何环境里挂在这 6 条账单上的支付单/流水/收据/发票/调整/通知(含本机真测产生的那一笔)。
-- 不碰: 应收登记表(fin_receivable_register*)与登记表生成的账单(code RR* 且 source='应收登记表')。
-- 安全: 删除前整行备份到 bak_demo_seed_* 表;删多少行记入 bak_demo_seed_audit;可重复执行(重跑为空操作)。
-- 恢复: 其余表 INSERT INTO fin_x SELECT * FROM bak_demo_seed_fin_x;
--      fin_bill 因含生成列 billing_active_key,恢复要用下面备份语句里的显式列清单(两侧同列表)。

-- ---------- 0) 备份与留痕表 ----------
CREATE TABLE IF NOT EXISTS bak_demo_seed_fin_bill        LIKE fin_bill;
CREATE TABLE IF NOT EXISTS bak_demo_seed_fin_payment     LIKE fin_payment;
CREATE TABLE IF NOT EXISTS bak_demo_seed_fin_flow        LIKE fin_flow;
CREATE TABLE IF NOT EXISTS bak_demo_seed_fin_receipt     LIKE fin_receipt;
CREATE TABLE IF NOT EXISTS bak_demo_seed_fin_receipt_log LIKE fin_receipt_log;
CREATE TABLE IF NOT EXISTS bak_demo_seed_fin_pay_notice  LIKE fin_pay_notice;
CREATE TABLE IF NOT EXISTS bak_demo_seed_fin_invoice     LIKE fin_invoice;
CREATE TABLE IF NOT EXISTS bak_demo_seed_fin_adjust      LIKE fin_adjust;
CREATE TABLE IF NOT EXISTS bak_demo_seed_audit (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name   VARCHAR(64) NOT NULL,
    rows_deleted INT         NOT NULL,
    executed_at  DATETIME(3) NOT NULL
) COMMENT 'V42 演示数据清理行数留痕';

-- ---------- 1) 备份(以 6 条种子账单编号为根,按 id 防重,重跑不重复) ----------
-- fin_bill 有 STORED GENERATED 列 billing_active_key,LIKE 建的备份表同样生成它,
-- 故两侧都走显式列清单(V41 时点 schema 的全部非生成列),生成列由备份表自行计算
INSERT INTO bak_demo_seed_fin_bill
       (id,code,billing_key,contract_id,receivable_register_id,receivable_rule_id,tenant_ref_id,
        project_id,building_id,room_id,direction,fee_type,source,status,amount,paid_amount,late_fee,
        tax_rate,period_start,period_end,due_date,overdue_days,invoice_status,remark,tenant_id,
        create_by,create_time,update_by,update_time,version,deleted)
SELECT  b.id,b.code,b.billing_key,b.contract_id,b.receivable_register_id,b.receivable_rule_id,b.tenant_ref_id,
        b.project_id,b.building_id,b.room_id,b.direction,b.fee_type,b.source,b.status,b.amount,b.paid_amount,b.late_fee,
        b.tax_rate,b.period_start,b.period_end,b.due_date,b.overdue_days,b.invoice_status,b.remark,b.tenant_id,
        b.create_by,b.create_time,b.update_by,b.update_time,b.version,b.deleted
  FROM fin_bill b
  LEFT JOIN bak_demo_seed_fin_bill k ON k.id = b.id
 WHERE k.id IS NULL
   AND b.code IN ('ZD2026010001','ZD2026040002','ZD2026070003',
                  'ZD2026030004','ZD2026060005','ZD2026010006');

INSERT INTO bak_demo_seed_fin_payment
SELECT p.* FROM fin_payment p
  LEFT JOIN bak_demo_seed_fin_payment k ON k.id = p.id
 WHERE k.id IS NULL
   AND p.bill_id IN (SELECT id FROM bak_demo_seed_fin_bill);

INSERT INTO bak_demo_seed_fin_flow
SELECT f.* FROM fin_flow f
  LEFT JOIN bak_demo_seed_fin_flow k ON k.id = f.id
 WHERE k.id IS NULL
   AND (f.bill_id    IN (SELECT id FROM bak_demo_seed_fin_bill)
     OR f.payment_id IN (SELECT id FROM bak_demo_seed_fin_payment));

INSERT INTO bak_demo_seed_fin_receipt
SELECT r.* FROM fin_receipt r
  LEFT JOIN bak_demo_seed_fin_receipt k ON k.id = r.id
 WHERE k.id IS NULL
   AND (r.bill_id    IN (SELECT id FROM bak_demo_seed_fin_bill)
     OR r.payment_id IN (SELECT id FROM bak_demo_seed_fin_payment)
     OR r.receipt_no IN ('SJ2026070001','SJ2026070002'));

INSERT INTO bak_demo_seed_fin_receipt_log
SELECT l.* FROM fin_receipt_log l
  LEFT JOIN bak_demo_seed_fin_receipt_log k ON k.id = l.id
 WHERE k.id IS NULL
   AND l.receipt_id IN (SELECT id FROM bak_demo_seed_fin_receipt);

INSERT INTO bak_demo_seed_fin_pay_notice
SELECT n.* FROM fin_pay_notice n
  LEFT JOIN bak_demo_seed_fin_pay_notice k ON k.id = n.id
 WHERE k.id IS NULL
   AND (n.bill_id   IN (SELECT id FROM bak_demo_seed_fin_bill)
     OR n.notice_no IN ('TZ2026070001','TZ2026070002'));

INSERT INTO bak_demo_seed_fin_invoice
SELECT i.* FROM fin_invoice i
  LEFT JOIN bak_demo_seed_fin_invoice k ON k.id = i.id
 WHERE k.id IS NULL
   AND i.bill_id IN (SELECT id FROM bak_demo_seed_fin_bill);

INSERT INTO bak_demo_seed_fin_adjust
SELECT a.* FROM fin_adjust a
  LEFT JOIN bak_demo_seed_fin_adjust k ON k.id = a.id
 WHERE k.id IS NULL
   AND a.bill_id IN (SELECT id FROM bak_demo_seed_fin_bill);

-- ---------- 2) 删除(只删备份表里点过名的 id;先子后母) ----------
DELETE t FROM fin_receipt_log t JOIN bak_demo_seed_fin_receipt_log k ON t.id = k.id;
INSERT INTO bak_demo_seed_audit (table_name, rows_deleted, executed_at) VALUES ('fin_receipt_log', ROW_COUNT(), NOW(3));

DELETE t FROM fin_receipt t JOIN bak_demo_seed_fin_receipt k ON t.id = k.id;
INSERT INTO bak_demo_seed_audit (table_name, rows_deleted, executed_at) VALUES ('fin_receipt', ROW_COUNT(), NOW(3));

DELETE t FROM fin_flow t JOIN bak_demo_seed_fin_flow k ON t.id = k.id;
INSERT INTO bak_demo_seed_audit (table_name, rows_deleted, executed_at) VALUES ('fin_flow', ROW_COUNT(), NOW(3));

DELETE t FROM fin_pay_notice t JOIN bak_demo_seed_fin_pay_notice k ON t.id = k.id;
INSERT INTO bak_demo_seed_audit (table_name, rows_deleted, executed_at) VALUES ('fin_pay_notice', ROW_COUNT(), NOW(3));

DELETE t FROM fin_invoice t JOIN bak_demo_seed_fin_invoice k ON t.id = k.id;
INSERT INTO bak_demo_seed_audit (table_name, rows_deleted, executed_at) VALUES ('fin_invoice', ROW_COUNT(), NOW(3));

DELETE t FROM fin_adjust t JOIN bak_demo_seed_fin_adjust k ON t.id = k.id;
INSERT INTO bak_demo_seed_audit (table_name, rows_deleted, executed_at) VALUES ('fin_adjust', ROW_COUNT(), NOW(3));

DELETE t FROM fin_payment t JOIN bak_demo_seed_fin_payment k ON t.id = k.id;
INSERT INTO bak_demo_seed_audit (table_name, rows_deleted, executed_at) VALUES ('fin_payment', ROW_COUNT(), NOW(3));

DELETE t FROM fin_bill t JOIN bak_demo_seed_fin_bill k ON t.id = k.id;
INSERT INTO bak_demo_seed_audit (table_name, rows_deleted, executed_at) VALUES ('fin_bill', ROW_COUNT(), NOW(3));
