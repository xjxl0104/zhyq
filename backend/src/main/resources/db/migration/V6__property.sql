-- =====================================================================
-- V6 物业:报修工单 + 流转 + SLA + 巡检 + 会议室
-- =====================================================================

-- 工单(报修/巡检整改/告警处置统一承载)
CREATE TABLE pm_work_order (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(64)  NOT NULL COMMENT '工单号(全局唯一)',
    order_type     VARCHAR(32)  NOT NULL DEFAULT '报修' COMMENT '报修/巡检/告警',
    title          VARCHAR(255) NOT NULL,
    project_id     BIGINT,
    building_id    BIGINT,
    room_id        BIGINT,
    location       VARCHAR(255) COMMENT '位置',
    category       VARCHAR(64)  COMMENT '分类/工种',
    urgency        TINYINT      DEFAULT 2 COMMENT '紧急度:1低 2中 3高',
    -- 状态:1待派单 2待接单 3处理中 4待验收 5已完成 6已关闭 7已超时
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '工单状态(附录B)',
    source         VARCHAR(32)  COMMENT '来源',
    contact        VARCHAR(64), contact_phone VARCHAR(20),
    images         TEXT,
    assignee       VARCHAR(32)  COMMENT '处理人',
    sla_respond_min INT         COMMENT '响应SLA(分钟)',
    sla_resolve_min INT         COMMENT '解决SLA(分钟)',
    arrive_time    DATETIME     COMMENT '到场时间',
    finish_time    DATETIME     COMMENT '完成时间',
    score          TINYINT      COMMENT '满意度评分1-5',
    remark         VARCHAR(500),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_wo_code (code, deleted),
    KEY idx_wo_status (status)
) COMMENT '工单';

-- 工单流转记录
CREATE TABLE pm_work_order_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id       BIGINT       NOT NULL,
    action         VARCHAR(32)  COMMENT '派单/接单/到场/处理/验收/评价/关闭',
    operator       VARCHAR(32),
    content        VARCHAR(500),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_wo (order_id)
) COMMENT '工单流转';

-- 会议室
CREATE TABLE pm_meeting_room (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(128) NOT NULL,
    project_id     BIGINT,
    capacity       INT          DEFAULT 0,
    equipment      VARCHAR(255),
    open_time      VARCHAR(64)  COMMENT '开放时间',
    fee_rule       VARCHAR(255) COMMENT '计费规则',
    status         TINYINT      NOT NULL DEFAULT 1,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '会议室';

-- 会议室预约
CREATE TABLE pm_meeting_booking (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id        BIGINT       NOT NULL,
    tenant_ref_id  BIGINT,
    booker         VARCHAR(64),
    start_time     DATETIME     NOT NULL,
    end_time       DATETIME     NOT NULL,
    -- 状态:1待核销 2已核销 3已取消 4已退款
    status         TINYINT      NOT NULL DEFAULT 1,
    fee            DECIMAL(12,2) DEFAULT 0,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_mb_room (room_id)
) COMMENT '会议室预约';

-- 巡检计划
CREATE TABLE pm_inspection_plan (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(128) NOT NULL,
    project_id     BIGINT,
    cycle          VARCHAR(32)  COMMENT '周期:每日/每周/每月',
    route          VARCHAR(255) COMMENT '路线',
    points         TEXT         COMMENT '点位',
    status         TINYINT      NOT NULL DEFAULT 1,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '巡检计划';
