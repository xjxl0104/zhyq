-- =====================================================================
-- V2 建筑与租控:项目、楼宇、楼层、房间、租金方案
-- =====================================================================

-- 项目
CREATE TABLE biz_project (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(64)  NOT NULL COMMENT '项目编码(全局唯一)',
    name          VARCHAR(128) NOT NULL COMMENT '项目名称',
    type          VARCHAR(32)  COMMENT '类型:产业园/写字楼/公寓/商业综合体',
    province      VARCHAR(32), city VARCHAR(32), district VARCHAR(32),
    address       VARCHAR(255),
    manage_area   DECIMAL(14,2) DEFAULT 0 COMMENT '管理面积㎡',
    build_area    DECIMAL(14,2) DEFAULT 0 COMMENT '建筑面积㎡',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1运营中 0停用',
    manager       VARCHAR(32)  COMMENT '项目负责人',
    remark        VARCHAR(255),
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_proj_code (code, deleted)
) COMMENT '项目';

-- 楼宇
CREATE TABLE biz_building (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    code          VARCHAR(64)  NOT NULL COMMENT '楼宇编码',
    name          VARCHAR(128) NOT NULL,
    floor_count   INT          DEFAULT 0 COMMENT '楼层数',
    build_area    DECIMAL(14,2) DEFAULT 0,
    usage_type    VARCHAR(32)  COMMENT '用途',
    status        TINYINT      NOT NULL DEFAULT 1,
    sort          INT          DEFAULT 0,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_proj (project_id)
) COMMENT '楼宇';

-- 楼层
CREATE TABLE biz_floor (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    building_id   BIGINT       NOT NULL,
    project_id    BIGINT       NOT NULL,
    name          VARCHAR(64)  NOT NULL COMMENT '楼层名称,如 3F/B1',
    floor_no      INT          COMMENT '楼层序号',
    build_area    DECIMAL(14,2) DEFAULT 0,
    sort          INT          DEFAULT 0,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_building (building_id)
) COMMENT '楼层';

-- 房间/房源
CREATE TABLE biz_room (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    floor_id       BIGINT       NOT NULL,
    building_id    BIGINT       NOT NULL,
    project_id     BIGINT       NOT NULL,
    code           VARCHAR(64)  NOT NULL COMMENT '房间编码(全局唯一)',
    room_no        VARCHAR(64)  NOT NULL COMMENT '房号',
    -- 状态:0未配置 1可租 2锁定 3意向占用 4签约中 5在租 6退租处理中 7维修 8停用
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '房源状态(附录B)',
    build_area     DECIMAL(12,2) DEFAULT 0 COMMENT '建筑面积',
    rent_area      DECIMAL(12,2) DEFAULT 0 COMMENT '计租面积',
    use_area       DECIMAL(12,2) DEFAULT 0 COMMENT '使用面积',
    orientation    VARCHAR(16)  COMMENT '朝向',
    decoration     VARCHAR(32)  COMMENT '装修',
    layout         VARCHAR(32)  COMMENT '户型',
    usage_type     VARCHAR(32)  COMMENT '用途',
    base_price     DECIMAL(12,2) DEFAULT 0 COMMENT '租金底价(元/㎡/月)',
    property_fee   DECIMAL(12,2) DEFAULT 0 COMMENT '物业费(元/㎡/月)',
    images         TEXT         COMMENT '图片URL,逗号分隔',
    remark         VARCHAR(255),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_room_code (code, deleted),
    KEY idx_floor (floor_id), KEY idx_proj_room (project_id), KEY idx_status (status)
) COMMENT '房间/房源';
