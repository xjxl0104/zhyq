-- =====================================================================
-- V132 全民营销 · 广东主力分区省钱锁、店铺榜单企查对齐30天缓存、高德地推500m打卡与飞鱼Webhook
-- 设计约束：
--   1. 零 ALTER 存量核心大表 crm_customer；仅扩展公海专属表 crm_public_sea_meta 并新建 4 张轻量独立表。
--   2. 统一 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci，经纬度与距离全部采用精确 DECIMAL。
-- =====================================================================

ALTER TABLE crm_public_sea_meta
    ADD COLUMN province            VARCHAR(32)   NULL DEFAULT '广东省' COMMENT '省份(默认广东省)',
    ADD COLUMN city                VARCHAR(32)   NULL COMMENT '城市(如广州市/东莞市/深圳市/佛山市/揭阳市)',
    ADD COLUMN district            VARCHAR(48)   NULL COMMENT '区县镇街(如白云区/番禺区/虎门镇/顺德区/普宁市)',
    ADD COLUMN zone_code           VARCHAR(24)   NOT NULL DEFAULT 'GZ' COMMENT '广东战区分区码:GZ/DG/SZ/FS/YD/OTHER_GD/OUT_GD',
    ADD COLUMN address             VARCHAR(255)  NULL COMMENT '高德/工商标准详细地址',
    ADD COLUMN building_name       VARCHAR(120)  NULL COMMENT '所属电商大厦或产业园名称(用于同楼多户聚合扫楼)',
    ADD COLUMN lng                 DECIMAL(10,6) NULL COMMENT 'GCJ-02经度',
    ADD COLUMN lat                 DECIMAL(10,6) NULL COMMENT 'GCJ-02纬度',
    ADD COLUMN nearest_wh_id       BIGINT        NULL COMMENT '最近本园加盟云仓ID',
    ADD COLUMN nearest_wh_name     VARCHAR(64)   NULL COMMENT '最近加盟云仓名称',
    ADD COLUMN dist_nearest_wh_km  DECIMAL(8,2)  NULL COMMENT '距最近加盟云仓直线/驾车估算距离(km)',
    ADD COLUMN credit_code         VARCHAR(32)   NULL COMMENT '统一社会信用代码',
    ADD COLUMN legal_person        VARCHAR(64)   NULL COMMENT '法定代表人',
    ADD COLUMN reg_capital         VARCHAR(64)   NULL COMMENT '注册资本描述',
    ADD COLUMN insured_count       INT           NULL COMMENT '社保参保人数',
    ADD COLUMN dishonest_count     INT           NOT NULL DEFAULT 0 COMMENT '失信被执行与限高命中条数(>0预警禁月结)',
    ADD COLUMN qcc_aligned         TINYINT       NOT NULL DEFAULT 0 COMMENT '工商对齐状态:0待补/未配Key 1已对齐 2外省锁跳过省钱',
    ADD COLUMN visit_protected_until DATETIME    NULL COMMENT '500m现场拍照打卡解锁的15天深度地推保护截止时间',
    ADD COLUMN last_checkin_dist_m INT           NULL COMMENT '最近一次现场GPS打卡距目标地址米数(<=500有效)',
    ADD COLUMN last_checkin_at     DATETIME      NULL COMMENT '最近一次现场GPS打卡时间',
    ADD COLUMN last_checkin_photo  VARCHAR(255)  NULL COMMENT '最近一次现场门头/快递堆实拍照片URL',
    ADD KEY idx_sea_zone_dist (tenant_id, project_id, zone_code, dist_nearest_wh_km),
    ADD KEY idx_sea_building (tenant_id, project_id, building_name);

ALTER TABLE crm_public_sea_wash_batch
    ADD COLUMN skipped_out_province INT NOT NULL DEFAULT 0 COMMENT '广东定向锁自动跳过外省免扣API费行数',
    ADD COLUMN qcc_cache_hits       INT NOT NULL DEFAULT 0 COMMENT '命中30天本地工商缓存免扣费次数',
    ADD COLUMN qcc_api_calls        INT NOT NULL DEFAULT 0 COMMENT '实际外发工商API扣费次数';

CREATE TABLE IF NOT EXISTS crm_ext_company_cache (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL DEFAULT 1,
    lookup_key      VARCHAR(120)  NOT NULL COMMENT '归一查询键(标准企业名或网店名)',
    company_name    VARCHAR(120)  NOT NULL COMMENT '标准营业执照企业全称',
    credit_code     VARCHAR(32)   NULL COMMENT '统一社会信用代码',
    legal_person    VARCHAR(64)   NULL,
    reg_capital     VARCHAR(64)   NULL,
    insured_count   INT           NULL,
    phone           VARCHAR(32)   NULL COMMENT '工商年报/公开实名电话',
    province        VARCHAR(32)   NULL,
    city            VARCHAR(32)   NULL,
    district        VARCHAR(48)   NULL,
    address         VARCHAR(255)  NULL,
    dishonest_count INT           NOT NULL DEFAULT 0,
    shops_json      JSON          NULL COMMENT '关联名下电商店铺矩阵快照',
    raw_source      VARCHAR(32)   NOT NULL DEFAULT 'ALIYUN_QCC' COMMENT '数据通道标识',
    expires_at      DATETIME      NOT NULL COMMENT '默认缓存30天有效截止时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ext_company_lookup (tenant_id, lookup_key),
    KEY idx_ext_company_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部工商与网店关联信息30天本地省钱缓存表';

CREATE TABLE IF NOT EXISTS crm_public_sea_shop (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     BIGINT       NOT NULL COMMENT '关联 crm_customer.id',
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    platform        VARCHAR(24)  NOT NULL DEFAULT '抖音' COMMENT '平台:抖音/天猫/淘宝/拼多多/1688/视频号',
    shop_name       VARCHAR(120) NOT NULL COMMENT '网店名称',
    category        VARCHAR(64)  NULL COMMENT '店铺主营类目(如个人洗护/女装)',
    rank_no         INT          NULL COMMENT '类目榜单排名(如洗护TOP500第18名)',
    monthly_sales   INT          NULL COMMENT '近30天销量单数',
    logistics_score DECIMAL(4,2) NULL COMMENT '店铺物流体验分',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sea_customer_shop (customer_id, platform, shop_name),
    KEY idx_sea_shop_plat (tenant_id, platform, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公海客户1客多店电商矩阵表';

CREATE TABLE IF NOT EXISTS crm_public_sea_checkin (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     BIGINT        NOT NULL,
    tenant_id       BIGINT        NOT NULL DEFAULT 1,
    project_id      BIGINT        NULL,
    operator        VARCHAR(64)   NOT NULL COMMENT '外勤打卡专员账号',
    checkin_lng     DECIMAL(10,6) NOT NULL COMMENT '专员现场GCJ-02经度',
    checkin_lat     DECIMAL(10,6) NOT NULL COMMENT '专员现场GCJ-02纬度',
    distance_m      INT           NOT NULL COMMENT '距目标客户坐标实测直线米数(<=500通过)',
    photo_url       VARCHAR(255)  NOT NULL COMMENT '现场门头或待发快递堆实拍照片路径',
    visit_note      VARCHAR(500)  NOT NULL COMMENT '当面拜访沟通纪要',
    protected_until DATETIME      NOT NULL COMMENT '本次打卡解锁的15天保护期截止时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sea_checkin_cust (customer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公海地推500m GPS定位实拍打卡审计留痕表';

CREATE TABLE IF NOT EXISTS crm_ext_ad_lead_inbox (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    channel         VARCHAR(24)  NOT NULL DEFAULT 'FEIYU' COMMENT 'FEIYU/TENCENT',
    external_lead_id VARCHAR(64) NOT NULL COMMENT '巨量飞鱼/腾讯线索唯一ID(幂等去重)',
    customer_id     BIGINT       NULL COMMENT '清洗入池后关联的customer_id(被防撞拦截则为空)',
    wash_result     VARCHAR(32)  NOT NULL COMMENT 'INSERTED/BLOCKED_PRIVATE/BLOCKED_HMAC/SKIPPED',
    payload_digest  CHAR(64)     NULL COMMENT 'SHA-256摘要排错不落敏感明文',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ad_lead_ext (channel, external_lead_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巨量飞鱼与腾讯广告免费Webhook幂等接收箱';

-- 预置广东定向省钱锁与地推打卡规则参数（外部接口密钥统一放在 V133 连接器中心表，不在 biz_setting 存明文）
INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted)
SELECT 'marketing', t.k, t.v, t.r, 1, 'system', NOW(), 1, 0
FROM (
    SELECT 'sea_region_lock_province' AS k, '广东省' AS v, '公海外部付费API定向省钱锁省份（非本省主体自动跳过付费工商查询，填 ALL 不限省）' AS r
    UNION ALL SELECT 'sea_checkin_max_meters', '500', '公海地推现场GPS打卡最大允许偏差米数（默认500米内有效）'
    UNION ALL SELECT 'sea_visit_protect_days', '15',  '通过500米现场拍照打卡后授予的深度地推保护天数（默认15天）'
) t
WHERE NOT EXISTS (SELECT 1 FROM biz_setting b WHERE b.module = 'marketing' AND b.skey = t.k AND b.deleted = 0);
