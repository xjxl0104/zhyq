-- V26__asset_management.sql 资产管理域（#20）
-- 全新 am_* 模块:资产台账 + 签出/签入状态机 + 盘点,挂 #3 空间树。
-- 与既有 pm_asset(物业设施台账,V10)完全独立,互不影响。
-- 财务边界:price 仅记录,绝不入账/折旧/写 finance(同 #23 fee)。

-- 资产台账
CREATE TABLE am_asset (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    asset_no        VARCHAR(64)   NOT NULL COMMENT '资产编号(唯一)',
    name            VARCHAR(128)  NOT NULL COMMENT '资产名称',
    category        VARCHAR(32)   NULL COMMENT 'IT/OFFICE/FURNITURE/DEVICE/VEHICLE/OTHER 六类枚举',
    space_id        BIGINT        NULL COMMENT '所在空间(挂 #3 sys_space 树)',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1在库 2领用中 3维修 4报废',
    holder          VARCHAR(64)   NULL COMMENT '当前持有人(占位/角色码,#7后真实)',
    purchase_date   DATE          NULL COMMENT '购置日期',
    price           DECIMAL(12,2) NULL COMMENT '购置价(仅记录,不入账不折旧)',
    remark          VARCHAR(255)  NULL COMMENT '备注',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_no (asset_no),
    KEY idx_category_status (category, status),
    KEY idx_space (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产台账';

-- 签出/签入/盘点流水
CREATE TABLE am_asset_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    asset_id        BIGINT        NOT NULL COMMENT '资产ID',
    action          VARCHAR(16)   NOT NULL COMMENT 'CHECKOUT/CHECKIN/INVENTORY/SCRAP/REPAIR',
    operator        VARCHAR(64)   NULL COMMENT '操作人(占位)',
    holder          VARCHAR(64)   NULL COMMENT '签出目标人',
    space_id        BIGINT        NULL COMMENT '盘点/移动到的空间',
    remark          VARCHAR(255)  NULL COMMENT '备注',
    act_time        DATETIME      NULL COMMENT '操作时间',
    tenant_id       BIGINT        NULL,
    create_by       VARCHAR(64)   NULL,
    create_time     DATETIME      NULL,
    update_by       VARCHAR(64)   NULL,
    update_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产签出签入/盘点流水';

-- 种子资产
INSERT INTO am_asset (asset_no, name, category, space_id, status, holder, purchase_date, price, remark, create_by, create_time, version, deleted) VALUES
('AM-IT-0001',   '联想笔记本 ThinkPad X1', 'IT',        NULL, 1, NULL, '2025-03-10', 8999.00, '研发部备机',   'system', NOW(), 0, 0),
('AM-OFF-0001',  '爱普生投影仪',           'OFFICE',    NULL, 1, NULL, '2024-11-20', 4200.00, 'A座301会议室', 'system', NOW(), 0, 0),
('AM-FUR-0001',  '升降办公桌',             'FURNITURE', NULL, 2, '张三','2025-01-05', 1580.00, '已领用',       'system', NOW(), 0, 0),
('AM-DEV-0001',  '巡检对讲机',             'DEVICE',    NULL, 1, NULL, '2025-05-18', 360.00,  '物业组',       'system', NOW(), 0, 0),
('AM-VEH-0001',  '园区电瓶巡逻车',         'VEHICLE',   NULL, 3, NULL, '2023-08-01', 32000.00,'维修中',       'system', NOW(), 0, 0);
