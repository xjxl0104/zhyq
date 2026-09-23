-- Supplements keep each already-issued bill / paid batch immutable. The line-level
-- unique keys from V67 remain the ultimate protection against double charging.
ALTER TABLE crm_warehouse_settlement DROP INDEX uk_warehouse_settle_period,
  ADD KEY idx_warehouse_settle_period (warehouse_id, period_start, period_end);
ALTER TABLE crm_direct_sign_payment ADD COLUMN period_start DATE NULL,
  ADD COLUMN period_end DATE NULL;
-- ERP order numbers are unique within a warehouse; other event sources stay global.
ALTER TABLE crm_referral_order
  ADD COLUMN source_scope BIGINT GENERATED ALWAYS AS
    (CASE WHEN source_type = 2 THEN COALESCE(warehouse_id, 0) ELSE 0 END) STORED,
  DROP INDEX uk_referral_order_source,
  ADD UNIQUE KEY uk_referral_order_source (source_type, source_scope, source_no);
