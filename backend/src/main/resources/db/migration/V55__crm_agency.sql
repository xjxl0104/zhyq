-- V55__crm_agency.sql 招商「渠道管理」改为「中介管理」
--
-- 复用 crm_channel 表(佣金 crm_commission.channel_id 依赖它),只加列不改老数据;
-- 另建中介跟进记录表,口径参照线索的 crm_follow。

-- ==================== 中介:补齐台账字段 ====================
ALTER TABLE crm_channel
    ADD COLUMN agency_no        VARCHAR(32)  NULL COMMENT '中介编号 ZJ-0001,服务端生成' AFTER id,
    ADD COLUMN agency_type      VARCHAR(64)  NULL COMMENT '中介类型:中介公司/个人经纪人/物流·电商服务商/商会·协会/其他',
    ADD COLUMN wechat           VARCHAR(64)  NULL COMMENT '微信号',
    ADD COLUMN region           VARCHAR(128) NULL COMMENT '所在地区/覆盖区域',
    ADD COLUMN address          VARCHAR(255) NULL COMMENT '办公地址',
    ADD COLUMN resource_desc    VARCHAR(255) NULL COMMENT '擅长业务/客户资源方向',
    ADD COLUMN grade            VARCHAR(32)  NULL COMMENT '合作等级:A-核心合作/B-一般合作/C-潜在合作',
    ADD COLUMN agreement_signed TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已签合作协议 0否 1是',
    ADD COLUMN owner_name       VARCHAR(64)  NULL COMMENT '园区对接负责人(自由文本)',
    ADD COLUMN referral_count   INT          NOT NULL DEFAULT 0 COMMENT '累计推荐客户数',
    ADD COLUMN deal_count       INT          NOT NULL DEFAULT 0 COMMENT '累计成交客户数',
    ADD COLUMN last_follow_date DATE         NULL COMMENT '最近跟进日期(由跟进记录自动回写)',
    ADD COLUMN follow_count     INT          NOT NULL DEFAULT 0 COMMENT '累计跟进次数(由跟进记录自动回写)',
    ADD COLUMN next_follow      DATETIME     NULL COMMENT '下次跟进计划(由跟进记录自动回写)';

-- 允许 NULL:老数据无编号时互不冲突
ALTER TABLE crm_channel ADD UNIQUE KEY uk_agency_no (agency_no);

-- ==================== 中介跟进记录 ====================
CREATE TABLE crm_channel_follow (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    follow_no       VARCHAR(32)  NULL COMMENT '跟进编号 ZF-0001,服务端生成',
    channel_id      BIGINT       NOT NULL COMMENT '所属中介',
    follow_date     DATE         NOT NULL COMMENT '跟进日期',
    type            VARCHAR(32)  NULL COMMENT '跟进方式:电话/微信·在线沟通/上门拜访/带客看仓/邮件/其他',
    follow_by       VARCHAR(64)  NULL COMMENT '跟进人',
    content         VARCHAR(1000) NULL COMMENT '沟通要点',
    referral_client VARCHAR(500) NULL COMMENT '本次推荐客户/房源需求',
    coop_change     VARCHAR(32)  NULL COMMENT '合作意向变化:明显升温/持平/降温/暂停合作',
    next_follow     DATETIME     NULL COMMENT '下次跟进计划',
    result          VARCHAR(500) NULL COMMENT '跟进结果/待办',
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    create_by       VARCHAR(32), create_time DATETIME, update_by VARCHAR(32), update_time DATETIME,
    version         INT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_channel_follow_no (follow_no),
    KEY idx_channel_follow (channel_id, follow_date)
) COMMENT '中介跟进记录';
