-- 聚合指标表
CREATE TABLE user_daily_metrics (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    dept_id             BIGINT      NOT NULL,
    stat_date           DATE        NOT NULL,
    active_minutes      INT         DEFAULT 0,
    request_count       INT         DEFAULT 0,
    module_set          JSON        DEFAULT NULL,
    core_action_count   INT         DEFAULT 0,
    flow_started        INT         DEFAULT 0,
    flow_completed      INT         DEFAULT 0,
    data_created        INT         DEFAULT 0,
    feedback_submitted  INT         DEFAULT 0,
    feedback_adopted    INT         DEFAULT 0,
    created_at          DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, stat_date),
    INDEX idx_dept_date (dept_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户日粒度指标';

CREATE TABLE user_period_metrics (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    dept_id             BIGINT      NOT NULL,
    period_type         TINYINT     NOT NULL COMMENT '1=周 2=月',
    period_start        DATE        NOT NULL,
    active_days         INT         DEFAULT 0,
    coverage_rate       DECIMAL(5,4) DEFAULT NULL,
    flow_close_rate     DECIMAL(5,4) DEFAULT NULL,
    total_core_actions  INT         DEFAULT 0,
    total_data_created  INT         DEFAULT 0,
    total_feedback      INT         DEFAULT 0,
    total_adopted       INT         DEFAULT 0,
    created_at          DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_period (user_id, period_type, period_start),
    INDEX idx_dept_period (dept_id, period_type, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户周月粒度指标';

CREATE TABLE user_score (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    dept_id         BIGINT      NOT NULL,
    period_type     TINYINT     NOT NULL COMMENT '1=周 2=月',
    period_start    DATE        NOT NULL,
    dim_coverage    TINYINT UNSIGNED DEFAULT 0,
    dim_flow        TINYINT UNSIGNED DEFAULT NULL,
    dim_frequency   TINYINT UNSIGNED DEFAULT 0,
    dim_data        TINYINT UNSIGNED DEFAULT 0,
    dim_feedback    TINYINT UNSIGNED DEFAULT 0,
    total_score     TINYINT UNSIGNED DEFAULT 0,
    created_at      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_period (user_id, period_type, period_start),
    INDEX idx_dept_period (dept_id, period_type, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户五维评分';
