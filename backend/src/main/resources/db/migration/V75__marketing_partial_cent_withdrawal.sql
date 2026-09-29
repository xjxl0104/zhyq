-- 原始佣金金额不变，部分提现以累计已提现额和本次锁定额记录；保留千分位权益。
ALTER TABLE crm_promoter_commission
    ADD COLUMN withdrawn_amount DECIMAL(15,3) NOT NULL DEFAULT 0 COMMENT '已成功提现/抵扣的累计金额',
    ADD COLUMN withdrawal_amount DECIMAL(15,3) NULL COMMENT '当前提现单锁定金额，旧待付款单为空';
ALTER TABLE crm_withdrawal
    ADD COLUMN commission_allocations JSON NULL COMMENT '本次逐佣金流水提现金额快照';
-- 历史已支付流水已经全部使用，累计金额应等于原金额（扣回行保留负号）。
UPDATE crm_promoter_commission SET withdrawn_amount = amount WHERE status = 4;
