-- V28__file.sql 统一附件表
-- 通用附件表:靠 biz_type + biz_id 关联任意业务对象(工单/合同/巡检/资料归档等)。
-- 不给各业务表加附件字段,统一走此表。文件落本地磁盘,库里只存元数据 + 访问 url。
CREATE TABLE sys_file (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    biz_type      VARCHAR(32)  NULL COMMENT '业务类型 work_order/contract/patrol/building_doc',
    biz_id        BIGINT       NULL COMMENT '关联业务主键,可空(先传后关联)',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    store_path    VARCHAR(512) NOT NULL COMMENT '相对存储路径 yyyy/MM/uuid.ext',
    url           VARCHAR(512) NOT NULL COMMENT '访问URL',
    file_size     BIGINT       NOT NULL DEFAULT 0 COMMENT '字节数',
    content_type  VARCHAR(128) NULL COMMENT 'MIME',
    ext           VARCHAR(32)  NULL COMMENT '扩展名',
    tenant_id     BIGINT       NULL DEFAULT 1,
    create_by     VARCHAR(64)  NULL,
    create_time   DATETIME     NULL,
    update_by     VARCHAR(64)  NULL,
    update_time   DATETIME     NULL,
    version       INT          NOT NULL DEFAULT 1,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一附件表';
