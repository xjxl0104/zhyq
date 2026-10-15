-- Append-only, evidence-backed order-count reconciliation. Legacy amount snapshots
-- cannot authorize a new settlement and are deliberately kept as LEGACY.
ALTER TABLE crm_erp_reconcile_snapshot
  DROP INDEX uk_erp_reconcile_period,
  ADD COLUMN metric VARCHAR(32) NOT NULL DEFAULT 'LEGACY_AMOUNT',
  ADD COLUMN state VARCHAR(24) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN revision INT NOT NULL DEFAULT 1,
  ADD COLUMN previous_snapshot_id BIGINT NULL,
  ADD COLUMN external_order_count BIGINT NULL,
  ADD COLUMN system_order_count BIGINT NULL,
  ADD COLUMN diff_order_count BIGINT NULL,
  ADD COLUMN blocking_count INT NOT NULL DEFAULT 0,
  ADD COLUMN source_name VARCHAR(200) NULL,
  ADD COLUMN evidence VARCHAR(1000) NULL,
  ADD COLUMN data_fingerprint VARCHAR(64) NULL,
  ADD COLUMN details_json JSON NULL,
  ADD COLUMN confirmed_by VARCHAR(64) NULL,
  ADD COLUMN confirmed_at DATETIME NULL,
  ADD UNIQUE KEY uk_erp_reconcile_revision (warehouse_id, period_start, period_end, revision),
  ADD KEY idx_erp_reconcile_state (state, warehouse_id);
ALTER TABLE crm_warehouse_settlement
  ADD COLUMN reconcile_snapshot_id BIGINT NULL,
  ADD KEY idx_warehouse_settle_reconcile (reconcile_snapshot_id);
CREATE INDEX idx_erp_inbox_warehouse_time ON crm_erp_event_inbox (warehouse_id, occurred_at);
CREATE INDEX idx_erp_reconcile_outbound ON crm_referral_order (warehouse_id, source_type, event_time);
