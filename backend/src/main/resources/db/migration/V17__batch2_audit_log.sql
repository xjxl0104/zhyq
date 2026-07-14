-- 批次② 操作审计日志表(§审计)
-- 由 @OperationLog 注解 + AOP 切面写入;操作人暂为 system(登录鉴权 #7 落地后填真实用户)
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    module      VARCHAR(64)           DEFAULT NULL COMMENT '业务模块',
    action      VARCHAR(128)          DEFAULT NULL COMMENT '操作描述',
    method      VARCHAR(256)          DEFAULT NULL COMMENT '目标方法',
    http_method VARCHAR(16)           DEFAULT NULL COMMENT 'HTTP 方法',
    req_uri     VARCHAR(256)          DEFAULT NULL COMMENT '请求 URI',
    params      TEXT                  DEFAULT NULL COMMENT '入参(截断)',
    operator    VARCHAR(64)           DEFAULT NULL COMMENT '操作人',
    ip          VARCHAR(64)           DEFAULT NULL COMMENT '客户端 IP',
    success     TINYINT      NOT NULL DEFAULT 1     COMMENT '是否成功 1是0否',
    error_msg   VARCHAR(512)          DEFAULT NULL COMMENT '异常信息',
    cost_ms     BIGINT                DEFAULT NULL COMMENT '耗时毫秒',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_oper_module (module),
    KEY idx_oper_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '操作审计日志';
