-- Direct-sign operational outbound records do not imply a marketing referral or commission.
-- Preserve all existing partner values; allow a customer without a referrer to keep order records.
ALTER TABLE crm_referral_order
  MODIFY COLUMN promoter_id BIGINT NULL COMMENT '成交伙伴；直签运营出库可为空';
