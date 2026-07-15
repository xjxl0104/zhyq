-- V25__resource_booking.sql 统一资源预订域（#23）
-- 全新 rsv_* 模块:资源目录 + 预订记录。费用只算金额存预订记录,绝不写账单/收款。

-- 资源目录（会议室/场地/工位统一）
CREATE TABLE rsv_resource (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    type            VARCHAR(16)   NOT NULL COMMENT '资源类型 MEETING/SITE/DESK',
    name            VARCHAR(128)  NOT NULL COMMENT '资源名',
    space_id        BIGINT        NULL COMMENT '挂空间树(#3),可空',
    capacity        INT           NULL COMMENT '容量',
    price_per_hour  DECIMAL(10,2) NULL COMMENT '单价/小时,空则不计费',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1可用 0停用',
    remark          VARCHAR(255)  NULL COMMENT '备注',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一资源目录';

-- 预订记录
CREATE TABLE rsv_booking (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    resource_id     BIGINT        NOT NULL COMMENT '资源ID',
    start_time      DATETIME      NOT NULL COMMENT '开始时间',
    end_time        DATETIME      NOT NULL COMMENT '结束时间',
    booker          VARCHAR(64)   NULL COMMENT '预订人(占位,#7后真实)',
    purpose         VARCHAR(255)  NULL COMMENT '用途',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1已预订 2已取消 3已完成',
    fee             DECIMAL(10,2) NULL COMMENT '算出的费用(时长×price,仅存不写账单)',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_resource_time (resource_id, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一预订记录';

-- 种子资源:一间会议室、一处场地、一个工位
INSERT INTO rsv_resource (type, name, space_id, capacity, price_per_hour, status, remark, create_by, create_time, version, deleted) VALUES
('MEETING', 'A座301会议室', NULL, 12, 50.00,  1, '带投影',   'system', NOW(), 0, 0),
('SITE',    '一楼多功能厅',   NULL, 200, 200.00, 1, '可办活动', 'system', NOW(), 0, 0),
('DESK',    '共享工位-W07',   NULL, 1,  8.00,   1, '开放区',   'system', NOW(), 0, 0);
