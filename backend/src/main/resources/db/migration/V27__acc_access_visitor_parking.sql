-- V27__acc_access_visitor_parking.sql 便捷通行域（#21）
-- 全新 acc_* 模块:门禁通行记录 + 访客预约到访 + 停车进出场。
-- 停车费只算只存于 acc_parking.fee,绝不写 fin_bill / 触发收款（设计 §7 财务边界）。

-- 访客预约/到访（状态机:1已预约 2已到访 3已离场 4已取消）
CREATE TABLE acc_visitor (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    name            VARCHAR(64)   NOT NULL COMMENT '访客姓名',
    phone           VARCHAR(32)   NULL COMMENT '访客电话',
    host_user       VARCHAR(64)   NULL COMMENT '被访人(占位,#7后接真实用户)',
    space_id        BIGINT        NULL COMMENT '到访地点(挂空间树#3),可空',
    visit_time      DATETIME      NULL COMMENT '预约到访时间',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1已预约 2已到访 3已离场 4已取消',
    plate_no        VARCHAR(16)   NULL COMMENT '随车车牌(可空,联动停车)',
    remark          VARCHAR(255)  NULL COMMENT '备注',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status_visit (status, visit_time),
    KEY idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客预约/到访';

-- 门禁通行记录（只增不改的流水,无状态机）
CREATE TABLE acc_access_record (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    gate_code       VARCHAR(64)   NOT NULL COMMENT '门/闸机编号',
    space_id        BIGINT        NULL COMMENT '门所在空间(挂空间树#3),可空',
    person_type     VARCHAR(16)   NULL COMMENT 'staff/visitor/unknown',
    person_ref      VARCHAR(64)   NULL COMMENT '人员标识(员工号/访客id/占位)',
    direction       TINYINT       NOT NULL DEFAULT 1 COMMENT '1进 2出',
    result          TINYINT       NOT NULL DEFAULT 1 COMMENT '1放行 2拒绝',
    access_time     DATETIME      NULL COMMENT '通行时刻',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_gate_time (gate_code, access_time),
    KEY idx_space (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门禁通行记录';

-- 停车（车辆 + 进出场 + 费用试算,状态机:1在场 2已离场）
CREATE TABLE acc_parking (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    plate_no        VARCHAR(16)   NOT NULL COMMENT '车牌',
    owner_type      VARCHAR(16)   NULL COMMENT 'staff/visitor/temp',
    enter_time      DATETIME      NULL COMMENT '入场时间',
    leave_time      DATETIME      NULL COMMENT '出场时间(空=在场)',
    fee             DECIMAL(10,2) NULL COMMENT '停车费试算金额(只算只存,不入账)',
    fee_rule        VARCHAR(255)  NULL COMMENT '计费说明',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1在场 2已离场',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_plate_status (plate_no, status),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车进出场';

-- 种子:两条访客预约、两条通行流水、一在场一已离场停车记录
INSERT INTO acc_visitor (name, phone, host_user, space_id, visit_time, status, plate_no, remark, create_by, create_time, version, deleted) VALUES
('张三', '13800000001', 'system', NULL, NOW(), 1, '沪A12345', '洽谈合作',   'system', NOW(), 0, 0),
('李四', '13800000002', 'system', NULL, NOW(), 2, NULL,       '已到访面试', 'system', NOW(), 0, 0);

INSERT INTO acc_access_record (gate_code, space_id, person_type, person_ref, direction, result, access_time, create_by, create_time, version, deleted) VALUES
('GATE-A-01', NULL, 'staff',   'EMP1001', 1, 1, NOW(), 'system', NOW(), 0, 0),
('GATE-A-01', NULL, 'visitor', '2',       1, 1, NOW(), 'system', NOW(), 0, 0);

INSERT INTO acc_parking (plate_no, owner_type, enter_time, leave_time, fee, fee_rule, status, create_by, create_time, version, deleted) VALUES
('沪A12345', 'visitor', NOW(), NULL, NULL, NULL, 1, 'system', NOW(), 0, 0),
('沪B67890', 'temp',    DATE_SUB(NOW(), INTERVAL 3 HOUR), NOW(), 5.00, '首2h免费,后¥5/h', 2, 'system', NOW(), 0, 0);
