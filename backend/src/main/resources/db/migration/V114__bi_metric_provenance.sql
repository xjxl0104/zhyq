ALTER TABLE user_daily_metrics ADD COLUMN facts_measured TINYINT NOT NULL DEFAULT 0;
ALTER TABLE user_period_metrics ADD COLUMN facts_measured TINYINT NOT NULL DEFAULT 0;
