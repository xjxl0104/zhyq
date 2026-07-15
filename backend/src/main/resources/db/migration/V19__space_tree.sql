-- V19__space_tree.sql  统一空间树主数据（叠加层，不动既有四表）
CREATE TABLE sys_space (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT       NULL COMMENT '父节点id，根为null',
    path        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '物化路径 /1/5/23/',
    level       TINYINT      NOT NULL DEFAULT 1 COMMENT '层级深度，1=根',
    type        VARCHAR(16)  NOT NULL COMMENT 'PROJECT/BUILDING/FLOOR/ROOM，预留PARK/ZONE/AREA/ASSET_LOC',
    code        VARCHAR(64)  NOT NULL COMMENT '统一空间编码',
    name        VARCHAR(128) NOT NULL DEFAULT '',
    ref_type    VARCHAR(16)  NOT NULL COMMENT 'project/building/floor/room',
    ref_id      BIGINT       NOT NULL COMMENT '旧表主键',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常0停用',
    tenant_id   BIGINT       NULL,
    create_by   VARCHAR(64)  NULL,
    create_time DATETIME     NULL,
    update_by   VARCHAR(64)  NULL,
    update_time DATETIME     NULL,
    version     INT          NOT NULL DEFAULT 0,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ref (ref_type, ref_id, deleted),
    UNIQUE KEY uk_code (code, deleted),
    KEY idx_parent (parent_id),
    KEY idx_path (path),
    KEY idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一空间树主数据';

ALTER TABLE biz_contract       ADD COLUMN space_id BIGINT NULL COMMENT '空间树节点id', ADD KEY idx_space_id (space_id);
ALTER TABLE biz_contract_room  ADD COLUMN space_id BIGINT NULL COMMENT '空间树节点id', ADD KEY idx_space_id (space_id);
ALTER TABLE pm_work_order      ADD COLUMN space_id BIGINT NULL COMMENT '空间树节点id', ADD KEY idx_space_id (space_id);
ALTER TABLE eng_reading        ADD COLUMN space_id BIGINT NULL COMMENT '空间树节点id', ADD KEY idx_space_id (space_id);
ALTER TABLE iot_device         ADD COLUMN space_id BIGINT NULL COMMENT '空间树节点id', ADD KEY idx_space_id (space_id);
