-- Each backfill transaction scans a single date. Build its range indexes online.
ALTER TABLE biz_contract ADD INDEX idx_bi_contract_created (create_time), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE fin_bill ADD INDEX idx_bi_bill_created (create_time), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE pm_work_order ADD INDEX idx_bi_order_created (create_time), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_instance ADD INDEX idx_bi_instance_created (create_time), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE wf_task ADD INDEX idx_bi_task_acted (act_time), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE suggestion ADD INDEX idx_bi_suggestion_created (create_time), ADD INDEX idx_bi_suggestion_resolved (resolved_at), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE user_daily_metrics ADD INDEX idx_bi_daily_date (stat_date), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE user_period_metrics ADD INDEX idx_bi_period_start (period_type, period_start), ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE user_score ADD INDEX idx_bi_score_start (period_type, period_start), ALGORITHM=INPLACE, LOCK=NONE;

CREATE TABLE bi_metric_history_backfill (
    id TINYINT NOT NULL PRIMARY KEY,
    next_day DATE NULL,
    through_day DATE NULL,
    finished_at DATETIME NULL
) ENGINE=InnoDB;
INSERT INTO bi_metric_history_backfill (id) VALUES (1);
