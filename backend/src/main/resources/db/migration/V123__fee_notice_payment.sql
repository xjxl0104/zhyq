-- 缴费登记独立于已付金额和应收金额，历史通知单默认未缴费。
ALTER TABLE eng_fee_notice
    ADD COLUMN payment_status TINYINT NOT NULL DEFAULT 0 COMMENT '缴费登记：0未缴费，1已缴费';
