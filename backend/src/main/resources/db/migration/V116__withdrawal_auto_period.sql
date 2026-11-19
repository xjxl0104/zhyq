ALTER TABLE crm_withdrawal
    ADD COLUMN auto_period CHAR(7) NULL,
    ADD UNIQUE KEY uk_withdrawal_auto_month (promoter_id, auto_period);
