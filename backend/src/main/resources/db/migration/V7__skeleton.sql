-- =====================================================================
-- V7 骨架模块:能耗、智慧物联(设备/告警)、办公、惠企、待办
-- =====================================================================

-- 表计
CREATE TABLE eng_meter (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(64)  NOT NULL COMMENT '表计编号',
    name           VARCHAR(128),
    energy_type    VARCHAR(16)  NOT NULL COMMENT '电/水/燃气/热力',
    project_id     BIGINT, building_id BIGINT, room_id BIGINT,
    ratio          DECIMAL(10,2) DEFAULT 1 COMMENT '倍率',
    last_reading   DECIMAL(14,2) DEFAULT 0 COMMENT '上次读数',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1在用 0停用',
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_meter_code (code, deleted)
) COMMENT '智能表计';

-- 抄表读数
CREATE TABLE eng_reading (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    meter_id       BIGINT       NOT NULL,
    prev_reading   DECIMAL(14,2) DEFAULT 0,
    curr_reading   DECIMAL(14,2) DEFAULT 0,
    usage_amount   DECIMAL(14,2) DEFAULT 0 COMMENT '用量',
    read_source    VARCHAR(16)  DEFAULT '自动采集' COMMENT '自动采集/人工/估算/补录/换表',
    read_time      DATETIME,
    fee            DECIMAL(14,2) DEFAULT 0,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_meter (meter_id)
) COMMENT '抄表读数';

-- 设备(统一物模型简化版)
CREATE TABLE iot_device (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NOT NULL,
    category       VARCHAR(32)  COMMENT '门禁/门锁/停车/充电桩/摄像头/传感器/空开/消防',
    vendor         VARCHAR(64)  COMMENT '厂商',
    project_id     BIGINT, building_id BIGINT,
    location       VARCHAR(255),
    -- 状态:0未激活 1在线 2离线 3故障 4维护 5停用
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '设备状态(附录B)',
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_dev_code (code, deleted), KEY idx_dev_cat (category)
) COMMENT '设备';

-- 告警
CREATE TABLE iot_alarm (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id      BIGINT,
    alarm_type     VARCHAR(64),
    level          TINYINT      DEFAULT 2 COMMENT '1低 2中 3高',
    -- 状态:1新建 2已确认 3处理中 4已恢复 5已关闭 6误报
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '告警状态(附录B)',
    location       VARCHAR(255),
    content        VARCHAR(500),
    alarm_time     DATETIME,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_alarm_status (status)
) COMMENT '设备告警';

-- 办公任务
CREATE TABLE oa_task (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    owner          VARCHAR(32)  COMMENT '负责人',
    priority       TINYINT      DEFAULT 2 COMMENT '1低 2中 3高',
    due_date       DATETIME,
    -- 状态:1待处理 2处理中 3已完成
    status         TINYINT      NOT NULL DEFAULT 1,
    source         VARCHAR(32)  COMMENT '来源:招商/物业/巡检/手工',
    content        VARCHAR(1000),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '办公任务';

-- 企业公告
CREATE TABLE oa_notice (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    content        TEXT,
    publish_time   DATETIME,
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1已发布 0草稿',
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '企业公告';

-- 访客预约(惠企)
CREATE TABLE srv_visitor (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    visitor_name   VARCHAR(64)  NOT NULL,
    phone          VARCHAR(20),
    host           VARCHAR(64)  COMMENT '受访人',
    tenant_ref_id  BIGINT,
    visit_time     DATETIME,
    reason         VARCHAR(255),
    plate_no       VARCHAR(32),
    qr_code        VARCHAR(128),
    -- 状态:1待审批 2已通过 3已到场 4已离场 5已失效
    status         TINYINT      NOT NULL DEFAULT 1,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '访客预约';

-- 商城商品(惠企骨架)
CREATE TABLE srv_product (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(128) NOT NULL,
    product_type   VARCHAR(32)  COMMENT '实物/虚拟/服务/预约',
    price          DECIMAL(12,2) DEFAULT 0,
    stock          INT          DEFAULT 0,
    sales          INT          DEFAULT 0,
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '商城商品';

-- 统一待办
CREATE TABLE sys_todo (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    biz_type       VARCHAR(32)  COMMENT 'contract/bill/workorder/lead/approval',
    biz_id         BIGINT,
    owner          VARCHAR(32),
    due_date       DATETIME,
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1待办 2已读 3已完成',
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_todo_type (biz_type)
) COMMENT '统一待办';
