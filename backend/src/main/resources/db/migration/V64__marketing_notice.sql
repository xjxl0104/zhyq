CREATE TABLE crm_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL COMMENT '收件云仓,查询强条件',
    type VARCHAR(32) NOT NULL COMMENT 'settlement.confirmed / settlement.disputed / bill.ready ...',
    title VARCHAR(128) NULL,
    content VARCHAR(512) NULL,
    biz_type VARCHAR(32) NULL COMMENT '关联业务类型(settlement/bill),供前端跳转',
    biz_id BIGINT NULL,
    read_at DATETIME NULL COMMENT '已读时间,null=未读',
    project_id BIGINT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_notice_wh_read (warehouse_id, read_at, id)
) COMMENT '全民营销云仓端通知';
