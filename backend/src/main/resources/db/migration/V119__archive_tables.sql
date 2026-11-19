-- V119 资料管理:分类表、自动归类规则表、资料表(纯新增,不动已有数据)
CREATE TABLE arc_category (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_code    VARCHAR(16)  NULL COMMENT '上级分类编码,门类为空',
    code           VARCHAR(16)  NOT NULL COMMENT '分类编码,如 03 / 03.04',
    name           VARCHAR(64)  NOT NULL COMMENT '分类名称',
    sort           INT          NOT NULL DEFAULT 0,
    default_secret TINYINT      NOT NULL DEFAULT 2 COMMENT '默认密级 1公开 2内部 3机密',
    enabled        TINYINT      NOT NULL DEFAULT 1,
    remark         VARCHAR(255) NULL,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_arc_category_code (code)
) COMMENT '资料分类(门类+子类)';

CREATE TABLE arc_rule (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword       VARCHAR(64)  NOT NULL COMMENT '关键词(包含匹配,不区分大小写)',
    scope         TINYINT      NOT NULL DEFAULT 1 COMMENT '匹配范围 1文件名与来源路径 2文件正文',
    category_code VARCHAR(16)  NULL COMMENT '命中后倾向的子类编码',
    doc_type      VARCHAR(16)  NULL COMMENT '命中后倾向的文档类型',
    secret_level  TINYINT      NULL COMMENT '命中后的密级 1公开 2内部 3机密',
    weight        INT          NOT NULL DEFAULT 6 COMMENT '权重 1~20,越大越有把握',
    enabled       TINYINT      NOT NULL DEFAULT 1,
    remark        VARCHAR(255) NULL,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_arc_rule_category (category_code)
) COMMENT '资料自动归类关键词规则';

CREATE TABLE arc_doc (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NULL,
    archive_no    VARCHAR(40)  NOT NULL COMMENT '档号:子类编码-年度-流水,如 03.01-2026-0001',
    name          VARCHAR(200) NOT NULL COMMENT '规范化名称',
    original_name VARCHAR(255) NULL COMMENT '上传时的原文件名',
    category_code VARCHAR(16)  NOT NULL COMMENT '子类编码',
    doc_type      VARCHAR(16)  NULL COMMENT '文档类型',
    secret_level  TINYINT      NOT NULL DEFAULT 2 COMMENT '密级 1公开 2内部 3机密',
    doc_date      DATE         NULL COMMENT '文件日期',
    period        VARCHAR(32)  NULL COMMENT '所属期间,如 2026年10月',
    version_label VARCHAR(32)  NULL COMMENT '版本/状态,如 盖章版',
    counterparty  VARCHAR(100) NULL COMMENT '对方单位/对象',
    expire_date   DATE         NULL COMMENT '到期/失效日期(合同、证照等)',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1现行 2失效',
    summary       VARCHAR(1000) NULL COMMENT '正文摘要(自动提取),用于检索',
    file_id       BIGINT       NULL COMMENT 'sys_file.id',
    file_ext      VARCHAR(16)  NULL,
    file_size     BIGINT       NULL,
    file_hash     CHAR(64)     NULL COMMENT '文件 SHA-256,用于查重',
    source_path   VARCHAR(500) NULL COMMENT '来源路径(批量导入时的原文件夹)',
    remark        VARCHAR(500) NULL,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_arc_doc_project (project_id, category_code),
    KEY idx_arc_doc_archive_no (archive_no),
    KEY idx_arc_doc_hash (file_hash),
    KEY idx_arc_doc_file (file_id)
) COMMENT '资料档案';

INSERT INTO route_module_mapping (route, module, module_name, is_core, enabled) VALUES
('/archive/doc/**', 'archive', '资料管理', 0, 1),
('/archive/setting/**', 'archive', '资料管理', 0, 1);
