-- V107 公摊度数手工填写
-- 线下对账表里公摊度数 = 总度数 − 租户用量,是手填的数。系统默认自动算,
-- 需要和线下表逐分对上时可在电费/水费页手工填公摊度数,留空则自动算。纯新增两列。
ALTER TABLE eng_fee_period
    ADD COLUMN electric_public_manual DECIMAL(16,2) NULL COMMENT '电公摊度数手工值,空=自动算' AFTER water_price,
    ADD COLUMN water_public_manual    DECIMAL(16,2) NULL COMMENT '水公摊度数手工值,空=自动算' AFTER electric_public_manual;

-- 「通知单已过期」靠 inputs_changed_time 与 generated_time 比先后,精确到毫秒,同一秒内先后操作也判得准
ALTER TABLE eng_fee_period
    MODIFY COLUMN generated_time DATETIME(3) NULL,
    MODIFY COLUMN inputs_changed_time DATETIME(3) NULL;
