-- =====================================================================
-- V3 招商 CRM + 租客
-- =====================================================================

-- 招商线索
CREATE TABLE crm_lead (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    contact       VARCHAR(64)  NOT NULL COMMENT '联系人',
    phone         VARCHAR(20)  NOT NULL,
    company       VARCHAR(128) COMMENT '公司',
    -- 状态:1新建 2待分配 3跟进中 4意向 5已转化 6无效 7公海 8审核中
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '线索状态(附录B)',
    source        VARCHAR(32)  COMMENT '来源:网页/小程序/活动/渠道/电话/人工',
    intent_project BIGINT      COMMENT '意向项目',
    demand_area   VARCHAR(32)  COMMENT '需求面积段',
    owner_id      BIGINT       COMMENT '负责人(招商)',
    channel_id    BIGINT       COMMENT '渠道',
    next_follow   DATETIME     COMMENT '下次跟进时间',
    remark        VARCHAR(500),
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_lead_status (status), KEY idx_owner (owner_id)
) COMMENT '招商线索';

-- 跟进记录
CREATE TABLE crm_follow (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    lead_id       BIGINT       NOT NULL,
    type          VARCHAR(32)  COMMENT '方式:电话/拜访/微信/邮件/会议',
    content       VARCHAR(1000),
    follow_by     VARCHAR(32),
    next_follow   DATETIME,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_lead (lead_id)
) COMMENT '跟进记录';

-- 渠道商
CREATE TABLE crm_channel (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    contact       VARCHAR(64), phone VARCHAR(20),
    commission_rate DECIMAL(6,2) DEFAULT 0 COMMENT '佣金比例%',
    status        TINYINT      NOT NULL DEFAULT 1,
    remark        VARCHAR(255),
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    create_by     VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version       INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0
) COMMENT '渠道商';

-- 租客
CREATE TABLE biz_tenant (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(64)  NOT NULL COMMENT '租客编码(全局唯一)',
    name           VARCHAR(128) NOT NULL COMMENT '名称',
    tenant_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '1企业 2个人',
    invite_code    VARCHAR(32)  COMMENT '邀请码',
    contact        VARCHAR(64)  COMMENT '联系人',
    phone          VARCHAR(20),
    project_id     BIGINT       COMMENT '所属园区',
    industry       VARCHAR(64)  COMMENT '行业',
    tags           VARCHAR(255) COMMENT '标签,逗号分隔',
    -- 企业专有
    credit_code    VARCHAR(32)  COMMENT '统一社会信用代码',
    legal_person   VARCHAR(64)  COMMENT '法人',
    reg_address    VARCHAR(255) COMMENT '注册地址',
    establish_date DATE         COMMENT '成立日期',
    biz_end_date   DATE         COMMENT '营业期限',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2已归档',
    remark         VARCHAR(255),
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_code (code, deleted)
) COMMENT '租客';

-- 租客员工
CREATE TABLE biz_tenant_staff (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_ref_id  BIGINT       NOT NULL COMMENT '所属租客',
    name           VARCHAR(64)  NOT NULL,
    dept           VARCHAR(64)  COMMENT '部门',
    post           VARCHAR(64)  COMMENT '岗位',
    phone          VARCHAR(20),
    access_perm    VARCHAR(255) COMMENT '门禁权限',
    plate_no       VARCHAR(32)  COMMENT '车牌',
    valid_end      DATE         COMMENT '有效期',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1在职 0离职',
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    create_by      VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version        INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_tenant_ref (tenant_ref_id)
) COMMENT '租客员工';
