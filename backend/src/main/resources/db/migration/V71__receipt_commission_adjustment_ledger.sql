-- Original rows retain amount/status/payment history. Receipt qualification has
-- a small independent state; every reversible settled adjustment gets a pair.
ALTER TABLE crm_promoter_commission
  ADD COLUMN adjustment_sequence INT NOT NULL DEFAULT 0 COMMENT '0 original/permanent; positive receipt cycle',
  ADD COLUMN receipt_revision INT NOT NULL DEFAULT 0 COMMENT 'last receipt cycle on original positive row',
  ADD COLUMN receipt_suspended TINYINT NOT NULL DEFAULT 0 COMMENT '0 eligible 1 suspended 2 permanently revoked',
  DROP INDEX uk_commission_order_payee,
  ADD UNIQUE KEY uk_commission_order_payee (referral_order_id, promoter_id, sign, adjustment_sequence);
-- Existing negative rows are permanent refunds/terminations, never recover them
-- automatically when a later receipt happens to arrive.
UPDATE crm_promoter_commission c
JOIN crm_promoter_commission debit ON debit.referral_order_id=c.referral_order_id
 AND debit.promoter_id=c.promoter_id AND debit.sign=-1 AND debit.deleted=0
SET c.receipt_suspended=2
WHERE c.sign=1 AND c.deleted=0;
UPDATE crm_promoter_commission SET receipt_suspended=2 WHERE sign=1 AND status=5;
