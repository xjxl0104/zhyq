-- =====================================================================
-- V1 系统管理:用户、角色、部门、岗位、字典、菜单权限
-- 所有核心表统一带:tenant_id / create_by / create_time / update_by /
-- update_time / version / deleted (规格书 §19.2)
-- =====================================================================

-- 部门
CREATE TABLE sys_dept (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID',
    name        VARCHAR(64)  NOT NULL COMMENT '部门名称',
    leader      VARCHAR(32)  COMMENT '负责人',
    phone       VARCHAR(20)  COMMENT '联系电话',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
    data_scope  TINYINT      NOT NULL DEFAULT 1 COMMENT '数据范围:1全部 2本部门及下级 3本部门 4仅本人 5指定',
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version     INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '部门';

-- 岗位
CREATE TABLE sys_post (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL COMMENT '岗位编码',
    name        VARCHAR(64)  NOT NULL COMMENT '岗位名称',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(255),
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version     INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '岗位';

-- 角色
CREATE TABLE sys_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL COMMENT '角色编码',
    name        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    data_scope  TINYINT      NOT NULL DEFAULT 1 COMMENT '数据范围',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(255),
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version     INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '角色';

-- 用户
CREATE TABLE sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL COMMENT '账号',
    nickname    VARCHAR(64)  NOT NULL COMMENT '昵称',
    dept_id     BIGINT       COMMENT '所属部门',
    post_id     BIGINT       COMMENT '岗位',
    phone       VARCHAR(20),
    email       VARCHAR(64),
    gender      TINYINT      DEFAULT 0 COMMENT '0未知 1男 2女',
    avatar      VARCHAR(255),
    user_type   TINYINT      NOT NULL DEFAULT 1 COMMENT '1平台用户 2租客用户',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    remark      VARCHAR(255),
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version     INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_username (username, deleted)
) COMMENT '用户';

-- 用户-角色关联
CREATE TABLE sys_user_role (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT NOT NULL,
    role_id  BIGINT NOT NULL,
    KEY idx_user (user_id)
) COMMENT '用户角色关联';

-- 菜单/权限
CREATE TABLE sys_menu (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    name        VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    type        TINYINT      NOT NULL DEFAULT 1 COMMENT '1目录 2菜单 3按钮',
    path        VARCHAR(128) COMMENT '路由路径',
    component   VARCHAR(128) COMMENT '前端组件',
    perm        VARCHAR(128) COMMENT '权限标识',
    icon        VARCHAR(64),
    sort        INT          NOT NULL DEFAULT 0,
    visible     TINYINT      NOT NULL DEFAULT 1,
    status      TINYINT      NOT NULL DEFAULT 1,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version     INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '菜单权限';

-- 角色-菜单关联
CREATE TABLE sys_role_menu (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id  BIGINT NOT NULL,
    menu_id  BIGINT NOT NULL,
    KEY idx_role (role_id)
) COMMENT '角色菜单关联';

-- 数据字典类型
CREATE TABLE sys_dict_type (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_type   VARCHAR(64)  NOT NULL COMMENT '字典类型编码',
    dict_name   VARCHAR(64)  NOT NULL COMMENT '字典名称',
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(255),
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version     INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_dict_type (dict_type, deleted)
) COMMENT '字典类型';

-- 数据字典项
CREATE TABLE sys_dict_data (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_type   VARCHAR(64)  NOT NULL COMMENT '字典类型编码',
    label       VARCHAR(64)  NOT NULL COMMENT '标签',
    value       VARCHAR(64)  NOT NULL COMMENT '值',
    color       VARCHAR(32)  COMMENT '颜色/标签样式',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(255),
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    create_by   VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version     INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_dict_type (dict_type)
) COMMENT '字典项';
