-- V122 资料管理改造:像电脑文件夹一样管理资料(纯新增 + 兼容旧数据)
-- 1) 新增文件夹表 arc_folder(无限层级,物化 id 路径便于子树查询与面包屑)
-- 2) arc_doc 增加 folder_id,分类编码改为可空(文件夹模式不再强制档案分类)

CREATE TABLE arc_folder (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id  BIGINT       NULL COMMENT '上级文件夹,根为空',
    name       VARCHAR(100) NOT NULL COMMENT '文件夹名称',
    path       VARCHAR(500) NOT NULL COMMENT '物化 id 路径,如 /1/5/12/,用于子树与面包屑',
    level      INT          NOT NULL DEFAULT 1 COMMENT '层级,根为1',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '同级排序',
    tenant_id  BIGINT       NOT NULL DEFAULT 1,
    create_by VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_af_parent (parent_id),
    KEY idx_af_path (path)
) COMMENT '资料文件夹(多级,像电脑文件夹)';

ALTER TABLE arc_doc ADD COLUMN folder_id BIGINT NULL COMMENT '所属文件夹 arc_folder.id';
ALTER TABLE arc_doc ADD INDEX idx_arc_doc_folder (folder_id);
ALTER TABLE arc_doc MODIFY COLUMN category_code VARCHAR(16) NULL COMMENT '子类编码(旧档案分类,兼容保留,可为空)';

INSERT INTO route_module_mapping (route, module, module_name, is_core, enabled) VALUES
('/archive/folder/**', 'archive', '资料管理', 0, 1);
