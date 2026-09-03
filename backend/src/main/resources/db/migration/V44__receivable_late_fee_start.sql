-- V44: 应收登记表增加「滞纳金起算日」(2026-09-03 负责人口径:李万能逾期暂不收滞纳金,10 月起算)
-- 该日之前不计滞纳金(账单逾期状态/逾期天数照标),NULL = 默认口径(应收日与建单日取较晚者)。
-- 政策日只能把起算推后,不能提前到默认口径之前(见 LateFeeService.effectiveFeeStart)。
ALTER TABLE fin_receivable_register
    ADD COLUMN late_fee_start_date DATE NULL COMMENT '滞纳金起算日:该日前不计滞纳金;NULL=默认口径' AFTER deposit_difference;
