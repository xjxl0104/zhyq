-- 功能一：使用度统计
CREATE TABLE access_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '操作用户ID',
    dept_id     BIGINT       NOT NULL COMMENT '用户所属部门ID',
    route       VARCHAR(255) NOT NULL COMMENT '模板化路由',
    method      VARCHAR(10)  NOT NULL COMMENT 'HTTP方法',
    module      VARCHAR(64)  DEFAULT NULL COMMENT '所属模块',
    is_core     TINYINT(1)   DEFAULT 0 COMMENT '是否核心操作',
    status_code INT          NOT NULL COMMENT 'HTTP状态码',
    duration_ms INT          NOT NULL COMMENT '请求耗时ms',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, created_at),
    INDEX idx_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='API访问日志（保留6个月）';

CREATE TABLE access_log_exclude (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    pattern VARCHAR(255) NOT NULL COMMENT '排除路由模式，Ant风格',
    reason  VARCHAR(100) DEFAULT NULL COMMENT '排除原因',
    enabled TINYINT(1)   DEFAULT 1,
    UNIQUE KEY uk_pattern (pattern)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='访问日志排除清单';

INSERT INTO access_log_exclude (pattern, reason) VALUES
('/auth/**', '登录登出'),
('/actuator/**', '健康检查'),
('/doc/**', 'API文档'),
('/swagger-resources/**', 'API文档'),
('/file/download/**', '文件下载非业务操作'),
('/webjars/**', '静态资源'),
('/error', '错误页');

CREATE TABLE route_module_mapping (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    route       VARCHAR(255) NOT NULL COMMENT '路由前缀 /contract/**',
    module      VARCHAR(64)  NOT NULL COMMENT '模块标识',
    module_name VARCHAR(64)  NOT NULL COMMENT '模块中文名',
    is_core     TINYINT(1)   DEFAULT 0 COMMENT '是否核心操作',
    enabled     TINYINT(1)   DEFAULT 1,
    UNIQUE KEY uk_route (route)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='路由-模块映射表';

-- 功能二：建议与Bug提交
CREATE TABLE suggestion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(50)  NOT NULL COMMENT '标题',
    content         TEXT         DEFAULT NULL COMMENT '详细描述',
    type            TINYINT      NOT NULL COMMENT '1=Bug 2=建议 3=其他',
    module          VARCHAR(64)  DEFAULT NULL COMMENT '关联模块',
    source_url      VARCHAR(500) DEFAULT NULL COMMENT '提交时页面URL',
    user_agent      VARCHAR(255) DEFAULT NULL,
    user_id         BIGINT       NOT NULL,
    dept_id         BIGINT       NOT NULL,
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1待处理2已确认3处理中4已解决5已采纳6已关闭',
    priority        TINYINT      DEFAULT 0 COMMENT '0未定1低2中3高',
    assignee_id     BIGINT       DEFAULT NULL COMMENT '指派处理人',
    close_reason    VARCHAR(255) DEFAULT NULL COMMENT '关闭原因',
    resolved_at     DATETIME     DEFAULT NULL,
    tenant_id       BIGINT       DEFAULT NULL,
    create_by       VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)  DEFAULT NULL,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT          DEFAULT 0,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='建议与Bug提交';

CREATE TABLE suggestion_image (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    suggestion_id   BIGINT       NOT NULL,
    file_id         BIGINT       NOT NULL COMMENT '关联sys_file.id',
    sort_order      TINYINT      DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_suggestion (suggestion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='建议附图';

CREATE TABLE suggestion_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    suggestion_id   BIGINT       NOT NULL,
    action          VARCHAR(32)  NOT NULL COMMENT 'created/assigned/status_changed/commented',
    from_status     TINYINT      DEFAULT NULL,
    to_status       TINYINT      DEFAULT NULL,
    operator_id     BIGINT       NOT NULL,
    operator_name   VARCHAR(64)  NOT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_suggestion (suggestion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='建议操作日志';
