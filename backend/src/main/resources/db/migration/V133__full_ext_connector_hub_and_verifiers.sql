-- =====================================================================
-- V133 全民营销 · 7大域18通道全量热插拔外部数据连接器中心与验店/抽检/验票闭环
-- 设计约束：
--   1. 零 ALTER 存量核心表 crm_customer；新建 4 张独立表承载 18 通道注册、10秒官方扫码验店、快递100轨迹抽检与 6%专票验真。
--   2. 未配置商业密钥的通道默认 enabled=0 (STANDBY)，¥0 待机绝不报错；填入密钥开启后即插即用，受月度限额熔断与广东省钱锁保护。
--   3. 统一 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci。
-- =====================================================================

CREATE TABLE IF NOT EXISTS crm_ext_connector_config (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL DEFAULT 1,
    connector_code        VARCHAR(40)   NOT NULL COMMENT '连接器唯一编码(如 QCC_PRO / CHANMAMA_API / KUAIDI100)',
    category_code         VARCHAR(24)   NOT NULL COMMENT '所属7大域:CORP/ECOM/LBS/AD/LOGI/CALL/FIN',
    display_name          VARCHAR(96)   NOT NULL COMMENT '连接器中文名称',
    fee_tier              VARCHAR(24)   NOT NULL DEFAULT 'PAID_CALL' COMMENT 'FREE/LOW_PACK/PAID_CALL/PAID_SAAS',
    fee_desc              VARCHAR(96)   NOT NULL COMMENT '资费参考说明',
    description           VARCHAR(300)  NOT NULL COMMENT '业务用途说明',
    enabled               TINYINT       NOT NULL DEFAULT 0 COMMENT '独立启停开关:0待机(¥0) 1已启用',
    gd_lock_only          TINYINT       NOT NULL DEFAULT 0 COMMENT '是否受广东省定向省钱硬锁保护:1仅广东主体可调用 0不限',
    monthly_quota_limit   INT           NOT NULL DEFAULT 1000 COMMENT '单月最大调用次数熔断阈值(防跑飞超支)',
    usage_month           CHAR(7)       NOT NULL DEFAULT '' COMMENT '当前计数自然月 YYYY-MM',
    month_used_count      INT           NOT NULL DEFAULT 0 COMMENT '当月实际外发扣费次数',
    blocked_out_gd_count  INT           NOT NULL DEFAULT 0 COMMENT '累计被广东定向省钱锁拦截免扣费次数',
    cache_hit_count       INT           NOT NULL DEFAULT 0 COMMENT '累计命中本地30天缓存免扣费次数',
    field_schema_json     VARCHAR(500)  NOT NULL DEFAULT '[]' COMMENT '凭证表单字段名数组JSON',
    credentials_cipher    TEXT          NULL COMMENT '凭证配置(启用 ZHYQ_FIELD_ENCRYPTION_KEY 时存 AES-GCM v1:密文)',
    apply_url             VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '同事申领办理官网',
    apply_guide           VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '派给同事的分步申领指引',
    sort_order            INT           NOT NULL DEFAULT 100,
    last_health_status    VARCHAR(24)   NOT NULL DEFAULT 'STANDBY' COMMENT 'STANDBY/OK/QUOTA_FUSED/ERROR',
    last_health_msg       VARCHAR(255)  NULL,
    last_health_at        DATETIME      NULL,
    updated_by            VARCHAR(64)   NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ext_conn_code (tenant_id, connector_code),
    KEY idx_ext_conn_cat (tenant_id, category_code, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='7大域18通道全量热插拔外部数据连接器配置与月度熔断台账';

CREATE TABLE IF NOT EXISTS crm_shop_oauth_session (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL DEFAULT 1,
    project_id            BIGINT        NULL,
    customer_id           BIGINT        NOT NULL COMMENT '关联公海或私海客户 crm_customer.id',
    platform              VARCHAR(24)   NOT NULL DEFAULT 'DOUYIN' COMMENT 'DOUYIN/TAOBAO/PDD',
    verify_token          VARCHAR(64)   NOT NULL COMMENT '一次性加密验店邀请令牌',
    shop_name             VARCHAR(120)  NULL,
    status                VARCHAR(24)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/OAUTH_VERIFIED/SELF_REPORTED/EXPIRED',
    verified_30d_orders   INT           NULL COMMENT '核验近30天真实支付/发货单量',
    verified_daily_avg    INT           NULL COMMENT '折算真实日均单量',
    return_rate_pct       DECIMAL(5,2)  NULL COMMENT '近30天退货率(%)',
    avg_weight_kg         DECIMAL(6,2)  NULL COMMENT '核验平均件重(kg)',
    proof_note            VARCHAR(255)  NULL COMMENT '官方OAuth摘要或商家后台截图凭证说明',
    expires_at            DATETIME      NOT NULL,
    verified_at           DATETIME      NULL,
    created_by            VARCHAR(64)   NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shop_oauth_token (verify_token),
    KEY idx_shop_oauth_cust (customer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抖店/淘宝1688/拼多多10秒官方扫码验店会话与真实单量核验表';

CREATE TABLE IF NOT EXISTS crm_waybill_spot_check (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL DEFAULT 1,
    warehouse_id          BIGINT        NULL COMMENT '被抽检加盟云仓ID',
    settle_month          CHAR(7)       NOT NULL COMMENT '抽检归属月份 YYYY-MM',
    order_id              BIGINT        NULL COMMENT '关联出库计佣单ID',
    tracking_no           VARCHAR(64)   NOT NULL COMMENT '抽检快递运单号',
    carrier_code          VARCHAR(32)   NULL COMMENT '快递公司编码(yuantong/zhongtong/jtexpress/yunda/shunfeng)',
    check_status          VARCHAR(24)   NOT NULL COMMENT 'REAL_SIGNED/IN_TRANSIT/FAKE_EMPTY/STANDBY_MANUAL',
    origin_city           VARCHAR(48)   NULL COMMENT '核验始发揽收城市',
    latest_trace          VARCHAR(255)  NULL COMMENT '最新物流轨迹摘要',
    checked_by            VARCHAR(64)   NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_waybill_check_month (tenant_id, settle_month, warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快递100真实物流轨迹2%随机抽检防空包刷佣金审计表';

CREATE TABLE IF NOT EXISTS crm_invoice_verify_log (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL DEFAULT 1,
    withdrawal_id         BIGINT        NULL COMMENT '关联提现申请单ID',
    invoice_code          VARCHAR(32)   NULL COMMENT '发票代码(全电票可空)',
    invoice_no            VARCHAR(32)   NOT NULL COMMENT '发票号码(20位全电票号或8位传统票号)',
    invoice_date          VARCHAR(20)   NOT NULL COMMENT '开票日期 YYYY-MM-DD',
    seller_name           VARCHAR(120)  NULL COMMENT '销方名称(P4企业公户主体)',
    buyer_tax_no          VARCHAR(32)   NULL COMMENT '购方税号(须匹配本园区主体税号)',
    item_name             VARCHAR(120)  NULL COMMENT '发票品目(须含现代服务/商务辅助/招商推广/管理咨询)',
    tax_rate_pct          DECIMAL(5,2)  NULL COMMENT '税率(标准6.00%)',
    total_amount          DECIMAL(12,2) NOT NULL COMMENT '价税合计金额(元)',
    verify_status         VARCHAR(24)   NOT NULL COMMENT 'VERIFIED_VALID/INVALID_RULE/CANCELLED_RED/STANDBY_MANUAL',
    verify_msg            VARCHAR(255)  NOT NULL COMMENT '核验结论说明',
    verified_by           VARCHAR(64)   NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_inv_verify_wd (withdrawal_id, created_at),
    KEY idx_inv_verify_no (tenant_id, invoice_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='P4企业提现6%增值税专用发票OCR与税务总局真伪核验台账';

-- 幂等预置 7 大域 18 条全量外部数据连接器（全部默认 enabled=0 关闭；广告回调须填验签密钥后才可开启，付费接口待同事申领 Key 后开启）
INSERT INTO crm_ext_connector_config
  (tenant_id, connector_code, category_code, display_name, fee_tier, fee_desc, description, enabled, gd_lock_only, monthly_quota_limit, field_schema_json, apply_url, apply_guide, sort_order)
SELECT 1, t.code, t.cat, t.nm, t.tier, t.fdesc, t.descr, t.en, t.gd, t.quota, t.schema_j, t.url, t.guide, t.ord
FROM (
  SELECT 'QCC_PRO' AS code, 'CORP' AS cat, '企查查官方开放平台（旗舰工商+司法失信+名下网店）' AS nm,
         'PAID_CALL' AS tier, '商业按次 · 约¥0.15~0.30/次' AS fdesc,
         '穿透统一社会信用代码、法人高管、参保人数、失信被执行/限高案件及同执照网店矩阵，老赖自动锁死月结。' AS descr,
         0 AS en, 1 AS gd, 2000 AS quota, '["appKey","secretKey"]' AS schema_j,
         'https://openapi.qcc.com' AS url, '用园区营业执照认证企查查开放平台 → 申请企业工商照面+失信被执行+网店接口包 → 拿回 AppKey 与 SecretKey' AS guide, 10 AS ord
  UNION ALL SELECT 'TYC_PRO', 'CORP', '天眼查开放平台（企业信用与诉讼互备源）', 'PAID_CALL', '商业按次/年包',
         '与企查查互为主备，支持企业模糊检索、工商年报实名电话与法律诉讼风险穿透。',
         0, 1, 1000, '["token"]', 'https://open.tianyancha.com', '企业实名注册天眼查开放平台 → 开通工商基本信息与司法风险接口 → 复制 Authorization Token', 11
  UNION ALL SELECT 'LIGHT_CORP', 'CORP', '阿里云云市场 / APISpace 百元轻量工商包', 'LOW_PACK', '百元按次 · ¥50~199用一年',
         '未签企查查年框前的高性价比过渡包，配30天本地缓存与广东省钱锁，补齐执照法人与年报电话。',
         0, 1, 1000, '["appCode","endpoint"]', 'https://market.aliyun.com', '阿里云账号搜索「企业工商信息查询」购买百元按次包 → 复制32位 AppCode 与请求 URL(不含参数)', 12
  UNION ALL SELECT 'TUNGEE_API', 'CORP', '探迹拓客 OpenAPI（关键人实名手机+招打包工雷达）', 'PAID_SAAS', '商业SaaS/API',
         '精准补全电商老板/供应链总监实名手机号（剔代账财务号），并自动拉取广东新发「招打包工/仓管」企业。',
         0, 1, 1500, '["appKey","appSecret","endpoint"]', 'https://open.tungee.com', '联系探迹客户经理开通企业 OpenAPI 权限 → 拿回 AppKey、AppSecret 与接口地址', 13

  UNION ALL SELECT 'CHANMAMA_API', 'ECOM', '蝉妈妈开放数据 API（抖音低物流分爆单榜直拉）', 'PAID_SAAS', '商业数据API',
         '按广东发货地+类目一键在线拉取近30天热销且物流体验分<4.60的痛点抖店，免手工导Excel直入公海。',
         0, 1, 1000, '["appId","appSecret","endpoint"]', 'https://www.chanmama.com', '联系蝉妈妈商务开通企业数据 API → 获取 AppId、AppSecret 与榜单拉取网关地址', 20
  UNION ALL SELECT 'DIANTOU_API', 'ECOM', '店透巢 / 电霸 API（淘天/拼多多/1688广东货盘）', 'PAID_SAAS', '商业数据API',
         '筛选发货地在广州/东莞/佛山/深圳/揭阳的天猫淘宝、拼多多与1688源头厂店月销量与DSR物流评分。',
         0, 1, 1000, '["apiKey","endpoint"]', 'https://www.diantouchao.com', '开通店透巢或电霸企业API服务 → 获取 ApiKey 与请求网关 URL', 21
  UNION ALL SELECT 'OAUTH_DOUYIN', 'ECOM', '抖音抖店开放平台（10秒官方扫码验真单量）', 'FREE', '¥0官方接口 · 需服务商认证',
         '向意向大客发送抖店官方授权链接，店主手机确认10秒读取近30天真实支付单量与退货率，防口头虚报单量。',
         0, 0, 5000, '["appKey","appSecret","redirectUri"]', 'https://op.jinritemai.com', '用园区科技主体入驻抖店开放平台 → 创建工具/仓储服务应用 → 拿回 AppKey、AppSecret 与回调地址', 22
  UNION ALL SELECT 'OAUTH_TAOBAO', 'ECOM', '淘宝天猫 & 1688 开放平台（官方扫码验店）', 'PAID_SAAS', '官方接口 · 需开放平台认证',
         '核验淘系及1688源头厂近30天真实发货包裹数与件重分布，自动生成多云仓阶梯比价单。',
         0, 0, 5000, '["appKey","appSecret","redirectUri"]', 'https://open.taobao.com', '注册淘宝/1688开放平台企业开发者 → 申请交易汇总只读权限 → 拿回 AppKey 与 AppSecret', 23
  UNION ALL SELECT 'OAUTH_PDD', 'ECOM', '拼多多开放平台（爆品店真实日单量验真）', 'FREE', '¥0官方接口 · 需开发者审核',
         '针对日发千单以上的广东拼多多白牌工厂店，扫码核验真实日均出库单量与波峰系数。',
         0, 0, 5000, '["clientId","clientSecret","redirectUri"]', 'https://open.pinduoduo.com', '注册拼多多开放平台企业开发者 → 创建订单统计工具应用 → 获取 ClientId 与 ClientSecret', 24

  UNION ALL SELECT 'AMAP_LBS', 'LBS', '高德开放平台全套 LBS（编码+10km雷达+货车真车程）', 'FREE', '¥0企业免费配额(5000次/日)',
         '广东地址转GCJ-02坐标、周边10km商圈POI扫楼雷达、真实道路驾车距离耗时矩阵及500m打卡逆地理门牌核验。',
         0, 0, 150000, '["webServiceKey","webJsKey","securityJsCode"]', 'https://lbs.amap.com', '高德开放平台企业实名认证 → 创建应用并分别添加「Web服务 Key」与「Web端 JS API Key」', 30

  UNION ALL SELECT 'AD_FEIYU', 'AD', '巨量引擎「飞鱼 CRM」广告线索 Webhook（抖音）', 'FREE', '¥0免费实时推送',
         '抖音/今日头条广东本地推找仓表单 0 秒 HMAC 验签、幂等防重并过三道防撞闸直入公海热单池。',
         0, 0, 999999, '["signatureSecret"]', 'https://feiyu.oceanengine.com', '飞鱼CRM后台 › 设置 › 线索外部推送粘贴 /api/open/v1/ad-leads/feiyu → 将签名密钥填回 signatureSecret', 40
  UNION ALL SELECT 'AD_TENCENT', 'AD', '腾讯广告「线索通」免费 Webhook（朋友圈/视频号）', 'FREE', '¥0免费实时推送',
         '微信朋友圈、公众号与视频号广东找云仓广告表单 0 秒直推公海并联动企微群抢单。',
         0, 0, 999999, '["tokenSecret"]', 'https://e.qq.com', '腾讯广告线索通后台配置 Webhook 指向 /api/open/v1/ad-leads/tencent → 将 Token 填回 tokenSecret', 41
  UNION ALL SELECT 'AD_XHS', 'AD', '小红书「聚光平台」品牌找仓线索 Webhook', 'FREE', '¥0接口费(投流配套)',
         '承接小红书广东美妆/设计师女装品牌主私信留资表单，自动打 S 级高客单标签入公海。',
         0, 0, 999999, '["secret"]', 'https://ad.xiaohongshu.com', '小红书聚光平台 › 线索管理 › Webhook 指向 /api/open/v1/ad-leads/xhs → 复制密钥填回 secret', 42
  UNION ALL SELECT 'AD_BAIDU', 'AD', '百度营销「基木鱼 / 爱番番」搜索线索 Webhook', 'FREE', '¥0接口费(投流配套)',
         '承接主动搜索「广州番禺/东莞云仓代发多少钱」的高意向精准询盘，0秒落库抢首通。',
         0, 0, 999999, '["token"]', 'https://yingxiao.baidu.com', '百度基木鱼开发者中心配置线索推送 /api/open/v1/ad-leads/baidu → 复制 Token 填回 token', 43

  UNION ALL SELECT 'KUAIDI100', 'LOGI', '快递100 物流轨迹抽检 API（2%防空包刷佣金）', 'PAID_CALL', '按单约¥0.03/单(仅抽2%)',
         '月度佣金结算前自动按 2% 抽取云仓上报运单核验三通一达/极兔真实广东揽收与签收轨迹，命中空包自动冻结预警。',
         0, 0, 3000, '["customer","key","sampleRatePct"]', 'https://api.kuaidi100.com', '注册快递100企业API账号 → 充值百元体验包 → 复制企业 Customer 编号与授权 Key', 50

  UNION ALL SELECT 'IM_WEBHOOK', 'CALL', '企业微信 / 飞书内部招商群秒级抢单机器人', 'FREE', '¥0完全免费',
         '30秒算价器或四大投流新热单入池 3 秒内自动推送抢单卡片至内部招商群，并推送 72h 临期回收预警。',
         0, 0, 999999, '["webhookUrl"]', '企业微信/飞书客户端群设置', '在园区内部招商群点右上角「添加群机器人」→ 复制完整 Webhook URL 粘贴即可', 60
  UNION ALL SELECT 'PRIVACY_AXB', 'CALL', '阿里云 AXB 隐私小号电销外呼 & 通话自动保活', 'PAID_CALL', '通信费约¥0.06/分钟',
         '保护公海客户电话不外泄并防止专员高频外呼被封卡；通话时长 ≥30 秒自动回写首触达解除 72h 弹回。',
         0, 0, 5000, '["accessKeyId","accessKeySecret","poolKey","endpoint"]', 'https://dypls.console.aliyun.com', '阿里云号码隐私保护控制台开通 AXB 小号池 → 复制 AccessKeyId、AccessKeySecret 与 PoolKey', 61

  UNION ALL SELECT 'VAT_INVOICE', 'FIN', '6% 增值税专用发票 OCR 识别 + 税务总局真伪查验', 'PAID_CALL', '按张约¥0.15/张',
         'P4 事业部对公提现上传专票时，自动核验购方园区税号、现代服务品目、6%税率、价税合计及是否红冲作废。',
         0, 0, 500, '["appCode","endpoint","buyerTaxNo"]', 'https://market.aliyun.com', '阿里云云市场搜索「增值税发票查验」购买百元按次包 → 复制 AppCode、请求 URL 并填本园区纳税人识别号', 70
) t
WHERE NOT EXISTS (SELECT 1 FROM crm_ext_connector_config c WHERE c.tenant_id = 1 AND c.connector_code = t.code);
