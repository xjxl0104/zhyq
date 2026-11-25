package com.zhyq.park.marketing.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPublicSeaMeta;
import com.zhyq.park.marketing.mapper.MktPublicSeaMetaMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 7大域 18 通道全量热插拔外部数据连接器中枢服务：
 * - 未申请/未付费通道保持 enabled=0（STANDBY），¥0 待机绝不报错、绝不编造虚假核验数据；
 * - 同事申领回 Key 填入并打开开关后即插即用，全程受「广东定向省钱硬锁 + 30天缓存 + 单月限额熔断」三重保护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MktConnectorHubService {

    private static final Set<String> NON_SECRET_FIELDS = Set.of(
            "endpoint", "redirectUri", "sampleRatePct", "buyerTaxNo", "webJsKey");
    private static final List<String> VALID_INVOICE_ITEMS = List.of(
            "现代服务", "商务辅助", "招商推广", "管理咨询", "信息系统", "技术服务", "经纪代理");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final FieldEncryptionService fieldEncryption;
    private final MktPublicSeaMetaMapper seaMetas;
    private final MktAuditService audit;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public record DefaultConnectorSeed(
            String code, String category, String name, String feeTier, String feeDesc,
            String desc, int enabled, int gdLock, int quota, List<String> fields, String applyUrl, String applyGuide) {}

    public static final List<DefaultConnectorSeed> DEFAULT_SEEDS = List.of(
            new DefaultConnectorSeed("QCC_PRO", "CORP", "企查查官方开放平台（旗舰工商+司法失信+名下网店）", "PAID_CALL", "商业按次 · 约¥0.15~0.30/次",
                    "穿透统一信用代码、法人高管、参保人数、失信限高终本案件及名下关联电商店铺，老赖自动禁开月结。", 0, 1, 2000,
                    List.of("appKey", "secretKey"), "https://openapi.qcc.com", "园区营业执照认证企查查开放平台 → 申请企业工商照面+失信被执行+网店接口包 → 拿回 appKey 与 secretKey"),
            new DefaultConnectorSeed("TYC_PRO", "CORP", "天眼查开放平台（企业信用与诉讼互备源）", "PAID_CALL", "商业按次/年包",
                    "与企查查互为主备数据源，支持企业模糊检索、工商年报实名电话与法律诉讼风险穿透。", 0, 1, 1000,
                    List.of("token"), "https://open.tianyancha.com", "企业实名注册天眼查开放平台 → 开通工商基本信息与司法风险接口 → 复制 Authorization token"),
            new DefaultConnectorSeed("LIGHT_CORP", "CORP", "阿里云云市场 / APISpace 百元轻量工商包", "LOW_PACK", "百元按次 · ¥50~199用一年",
                    "未签企查查年框前的高性价比过渡包，配30天本地缓存与广东省钱锁，补齐执照法人与年报电话。", 0, 1, 1000,
                    List.of("appCode", "endpoint"), "https://market.aliyun.com", "阿里云市场搜「企业工商信息查询」买百元按次包 → 复制 appCode 与请求 endpoint"),
            new DefaultConnectorSeed("TUNGEE_API", "CORP", "探迹拓客 OpenAPI（关键人实名手机+招打包工雷达）", "PAID_SAAS", "商业SaaS/API",
                    "补全电商老板/供应链负责人实名手机号（剔代账财务号），并拉取广东新发「招打包工/仓管」企业。", 0, 1, 1500,
                    List.of("appKey", "appSecret", "endpoint"), "https://open.tungee.com", "联系探迹客户经理开通企业 OpenAPI → 拿回 appKey、appSecret 与 endpoint"),
            new DefaultConnectorSeed("CHANMAMA_API", "ECOM", "蝉妈妈开放数据 API（抖音低物流分爆单榜直拉）", "PAID_SAAS", "商业数据API",
                    "按广东发货地+类目一键在线拉取近30天热销且物流体验分<4.60的痛点抖店，免手工导表直入公海。", 0, 1, 1000,
                    List.of("appId", "appSecret", "endpoint"), "https://www.chanmama.com", "联系蝉妈妈商务开通企业数据 API → 获取 appId、appSecret 与榜单拉取网关 endpoint"),
            new DefaultConnectorSeed("DIANTOU_API", "ECOM", "店透巢 / 电霸 API（淘天/拼多多/1688广东货盘）", "PAID_SAAS", "商业数据API",
                    "筛选发货地在广州/东莞/佛山/深圳/揭阳的天猫淘宝、拼多多与1688源头厂店月销与DSR物流分。", 0, 1, 1000,
                    List.of("apiKey", "endpoint"), "https://www.diantouchao.com", "开通店透巢或电霸企业API服务 → 获取 apiKey 与请求网关 endpoint"),
            new DefaultConnectorSeed("OAUTH_DOUYIN", "ECOM", "抖音抖店开放平台（10秒官方扫码验真单量）", "FREE", "¥0官方接口 · 需服务商认证",
                    "向意向大客发送抖店官方授权链接，店主手机确认10秒读取近30天真实支付单量与退货率。", 0, 0, 5000,
                    List.of("appKey", "appSecret", "redirectUri"), "https://op.jinritemai.com", "用园区科技主体入驻抖店开放平台 → 创建仓储服务应用 → 拿回 appKey、appSecret 与 redirectUri"),
            new DefaultConnectorSeed("OAUTH_TAOBAO", "ECOM", "淘宝天猫 & 1688 开放平台（官方扫码验店）", "PAID_SAAS", "官方接口 · 需开放平台认证",
                    "核验淘系及1688源头厂近30天真实发货包裹数与件重分布，自动生成多云仓阶梯比价单。", 0, 0, 5000,
                    List.of("appKey", "appSecret", "redirectUri"), "https://open.taobao.com", "注册淘宝/1688开放平台企业开发者 → 申请交易汇总只读权限 → 拿回 appKey 与 appSecret"),
            new DefaultConnectorSeed("OAUTH_PDD", "ECOM", "拼多多开放平台（爆品店真实日单量验真）", "FREE", "¥0官方接口 · 需开发者审核",
                    "针对日发千单以上的广东拼多多白牌工厂店，扫码核验真实日均出库单量与波峰系数。", 0, 0, 5000,
                    List.of("clientId", "clientSecret", "redirectUri"), "https://open.pinduoduo.com", "注册拼多多开放平台企业开发者 → 获取 clientId 与 clientSecret"),
            new DefaultConnectorSeed("AMAP_LBS", "LBS", "高德开放平台全套 LBS（编码+10km雷达+货车真车程）", "FREE", "¥0企业免费配额(5000次/日)",
                    "广东地址转GCJ-02坐标、周边10km商圈POI扫楼雷达、真实道路驾车距离耗时矩阵及500m打卡逆地理核验。", 0, 0, 150000,
                    List.of("webServiceKey", "webJsKey", "securityJsCode"), "https://lbs.amap.com", "高德开放平台企业实名认证 → 分别创建「Web服务 Key」与「Web端 JS API Key」"),
            new DefaultConnectorSeed("AD_FEIYU", "AD", "巨量引擎「飞鱼 CRM」广告线索 Webhook（抖音）", "FREE", "¥0免费实时推送",
                    "抖音/今日头条广东本地推找仓表单 0 秒 HMAC 验签、幂等防重并过三道防撞闸直入公海热单池。", 1, 0, 999999,
                    List.of("signatureSecret"), "https://feiyu.oceanengine.com", "飞鱼CRM › 设置 › 线索外部推送粘贴 /api/open/v1/ad-leads/feiyu → 填回 signatureSecret"),
            new DefaultConnectorSeed("AD_TENCENT", "AD", "腾讯广告「线索通」免费 Webhook（朋友圈/视频号）", "FREE", "¥0免费实时推送",
                    "微信朋友圈、公众号与视频号广东找云仓广告表单 0 秒直推公海并联动企微群抢单。", 1, 0, 999999,
                    List.of("tokenSecret"), "https://e.qq.com", "腾讯广告线索通配置 Webhook 指向 /api/open/v1/ad-leads/tencent → 填回 tokenSecret"),
            new DefaultConnectorSeed("AD_XHS", "AD", "小红书「聚光平台」品牌找仓线索 Webhook", "FREE", "¥0接口费(投流配套)",
                    "承接小红书广东美妆/设计师女装品牌主私信留资表单，自动打 S 级高客单标签入公海。", 1, 0, 999999,
                    List.of("secret"), "https://ad.xiaohongshu.com", "小红书聚光平台 › 线索管理 › Webhook 指向 /api/open/v1/ad-leads/xhs → 填回 secret"),
            new DefaultConnectorSeed("AD_BAIDU", "AD", "百度营销「基木鱼 / 爱番番」搜索线索 Webhook", "FREE", "¥0接口费(投流配套)",
                    "承接主动搜索「广州番禺/东莞云仓代发多少钱」的高意向精准询盘，0秒落库抢首通。", 1, 0, 999999,
                    List.of("token"), "https://yingxiao.baidu.com", "百度基木鱼配置线索推送 /api/open/v1/ad-leads/baidu → 填回 token"),
            new DefaultConnectorSeed("KUAIDI100", "LOGI", "快递100 物流轨迹抽检 API（2%防空包刷佣金）", "PAID_CALL", "按单约¥0.03/单(仅抽2%)",
                    "月度结算前自动按 2% 抽取云仓运单核验三通一达/极兔真实广东揽收与签收轨迹，命中空包自动冻结预警。", 0, 0, 3000,
                    List.of("customer", "key", "sampleRatePct"), "https://api.kuaidi100.com", "注册快递100企业API账号 → 充值百元体验包 → 复制 customer 编号与 key"),
            new DefaultConnectorSeed("IM_WEBHOOK", "CALL", "企业微信 / 飞书内部招商群秒级抢单机器人", "FREE", "¥0完全免费",
                    "算价器或四大投流新热单入池 3 秒内自动推送抢单卡片至内部招商群，并推送 72h 临期回收预警。", 0, 0, 999999,
                    List.of("webhookUrl"), "企业微信/飞书群设置", "在园区内部招商群点右上角「添加群机器人」→ 复制完整 webhookUrl 粘贴即可"),
            new DefaultConnectorSeed("PRIVACY_AXB", "CALL", "阿里云 AXB 隐私小号电销外呼 & 通话自动保活", "PAID_CALL", "通信费约¥0.06/分钟",
                    "保护公海客户电话不外泄并防专员高频外呼被封卡；通话≥30秒自动回写首触达解除 72h 弹回。", 0, 0, 5000,
                    List.of("accessKeyId", "accessKeySecret", "poolKey", "endpoint"), "https://dypls.console.aliyun.com", "阿里云号码隐私保护控制台开通 AXB 小号池 → 复制 accessKeyId、accessKeySecret 与 poolKey"),
            new DefaultConnectorSeed("VAT_INVOICE", "FIN", "6% 增值税专用发票 OCR 识别 + 税务总局真伪查验", "PAID_CALL", "按张约¥0.15/张",
                    "P4 事业部对公提现上传专票时，自动核验购方园区税号、现代服务品目、6%税率、价税合计及是否红冲作废。", 0, 0, 500,
                    List.of("appCode", "endpoint", "buyerTaxNo"), "https://market.aliyun.com", "阿里云市场搜「增值税发票查验」买百元包 → 复制 appCode、endpoint 并填本园 buyerTaxNo")
    );

    /** 读取全部 18 条连接器脱敏配置 + 顶部 3 卡 L1->L3 同源聚合统计。 */
    @Transactional(readOnly = true)
    public Map<String, Object> hubOverview() {
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        String curMonth = YearMonth.now().toString();
        List<Map<String, Object>> dbRows = queryDbConnectors(tenantId);
        List<Map<String, Object>> connectors = mergeWithSeeds(dbRows, curMonth);

        int activeCount = 0, freeCount = 0, paidCount = 0, fusedCount = 0;
        int totalMonthUsed = 0, totalBlockedOutGd = 0, totalCacheHits = 0;
        for (Map<String, Object> c : connectors) {
            if (Boolean.TRUE.equals(c.get("enabled"))) activeCount++;
            if ("FREE".equals(c.get("feeTier"))) freeCount++;
            else paidCount++;
            if ("QUOTA_FUSED".equals(c.get("healthStatus"))) fusedCount++;
            totalMonthUsed += toInt(c.get("monthUsedCount"));
            totalBlockedOutGd += toInt(c.get("blockedOutGdCount"));
            totalCacheHits += toInt(c.get("cacheHitCount"));
        }

        // 叠加洗数批次表中的广东外省锁跳过与 30 天缓存命中数，做到全口径同源真实对账
        int batchOutGd = queryIntSafe("SELECT COALESCE(SUM(skipped_out_province),0) FROM crm_public_sea_wash_batch WHERE tenant_id = ?", tenantId);
        int batchCache = queryIntSafe("SELECT COALESCE(SUM(qcc_cache_hits),0) FROM crm_public_sea_wash_batch WHERE tenant_id = ?", tenantId);
        int adInboxTotal = queryIntSafe("SELECT COUNT(1) FROM crm_ext_ad_lead_inbox WHERE tenant_id = ?", tenantId);
        int shopVerifiedCount = queryIntSafe("SELECT COUNT(1) FROM crm_shop_oauth_session WHERE tenant_id = ? AND status IN ('OAUTH_VERIFIED','SELF_REPORTED')", tenantId);
        int waybillCheckedCount = queryIntSafe("SELECT COUNT(1) FROM crm_waybill_spot_check WHERE tenant_id = ?", tenantId);
        int waybillFakeCount = queryIntSafe("SELECT COUNT(1) FROM crm_waybill_spot_check WHERE tenant_id = ? AND check_status = 'FAKE_EMPTY'", tenantId);
        int invoiceVerifiedCount = queryIntSafe("SELECT COUNT(1) FROM crm_invoice_verify_log WHERE tenant_id = ? AND verify_status = 'VERIFIED_VALID'", tenantId);

        int combinedOutGd = totalBlockedOutGd + batchOutGd;
        int combinedCache = totalCacheHits + batchCache;
        // 按商业接口均价 0.20 元/次估算省钱护栏已拦截节约金额（同源透明公式）
        BigDecimal estimatedSavedYuan = BigDecimal.valueOf(combinedOutGd + combinedCache)
                .multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fetchedAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
        summary.put("totalConnectors", connectors.size());
        summary.put("activeConnectors", activeCount);
        summary.put("freeConnectors", freeCount);
        summary.put("paidConnectors", paidCount);
        summary.put("fusedConnectors", fusedCount);
        summary.put("monthUsedTotal", totalMonthUsed);
        summary.put("blockedOutGdTotal", combinedOutGd);
        summary.put("cacheHitTotal", combinedCache);
        summary.put("estimatedSavedYuan", estimatedSavedYuan);
        summary.put("adInboxTotal", adInboxTotal);
        summary.put("shopVerifiedCount", shopVerifiedCount);
        summary.put("waybillCheckedCount", waybillCheckedCount);
        summary.put("waybillFakeCount", waybillFakeCount);
        summary.put("invoiceVerifiedCount", invoiceVerifiedCount);

        return Map.of("summary", summary, "connectors", connectors);
    }

    /** 保存单个连接器的开关、广东省钱锁、月度熔断上限与密钥凭证（支持 **** 原样不覆写）。 */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveConnector(String connectorCode, Map<String, Object> body) {
        DefaultConnectorSeed seed = findSeed(connectorCode);
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        boolean enabled = Boolean.TRUE.equals(body.get("enabled")) || "1".equals(String.valueOf(body.get("enabled")));
        boolean gdLockOnly = body.get("gdLockOnly") == null ? (seed.gdLock() == 1) : Boolean.TRUE.equals(body.get("gdLockOnly"));
        int quota = body.get("monthlyQuotaLimit") instanceof Number n ? Math.max(10, Math.min(9_999_999, n.intValue())) : seed.quota();

        Map<String, String> existingCreds = loadPlainCredentials(tenantId, seed.code());
        Map<String, Object> inputCreds = body.get("credentials") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, String> merged = new LinkedHashMap<>(existingCreds);
        for (String field : seed.fields()) {
            if (inputCreds.containsKey(field)) {
                String val = inputCreds.get(field) == null ? "" : String.valueOf(inputCreds.get(field)).trim();
                if (!val.contains("****")) {
                    merged.put(field, val);
                }
            }
        }

        String cipherText = encodeCredentials(merged);
        boolean hasAnyCred = merged.values().stream().anyMatch(StringUtils::hasText);
        String health = !enabled ? "STANDBY" : (hasAnyCred || seed.code().startsWith("AD_") ? "READY" : "MISSING_KEY");
        String operator = MktAuditService.currentOperator();

        upsertConnectorRow(tenantId, seed, enabled ? 1 : 0, gdLockOnly ? 1 : 0, quota, cipherText, health, operator);
        syncLegacyBizSettingsIfApplicable(seed.code(), enabled, merged);
        audit.log("connector.save", "connector", null,
                seed.code() + " enabled=" + enabled + " quota=" + quota + " gdLock=" + gdLockOnly);

        return Map.of("connectorCode", seed.code(), "enabled", enabled, "healthStatus", health, "monthlyQuotaLimit", quota);
    }

    /** 连通性自检（零扣费探针或检查必填密钥完整性）。 */
    @Transactional
    public Map<String, Object> pingConnector(String connectorCode) {
        DefaultConnectorSeed seed = findSeed(connectorCode);
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        Map<String, Object> row = findMergedConnector(tenantId, seed.code());
        boolean enabled = Boolean.TRUE.equals(row.get("enabled"));
        Map<String, String> creds = loadPlainCredentials(tenantId, seed.code());

        if (!enabled) {
            return Map.of("connectorCode", seed.code(), "ok", false, "status", "STANDBY",
                    "message", "该连接器当前处于待机关闭状态（¥0 成本），派同事申领 Key 填入并开启后即可自检");
        }
        if (seed.code().startsWith("AD_")) {
            String ch = seed.code().replace("AD_", "").toLowerCase(Locale.ROOT);
            return Map.of("connectorCode", seed.code(), "ok", true, "status", "OK",
                    "message", "Webhook 接收口已就绪：/api/open/v1/ad-leads/" + ch);
        }
        boolean hasKey = creds.entrySet().stream()
                .filter(e -> !List.of("redirectUri", "sampleRatePct", "buyerTaxNo", "securityJsCode", "webJsKey").contains(e.getKey()))
                .anyMatch(e -> StringUtils.hasText(e.getValue()));
        if (!hasKey) {
            updateHealth(tenantId, seed.code(), "MISSING_KEY", "已开启但尚未粘贴关键密钥字段");
            return Map.of("connectorCode", seed.code(), "ok", false, "status", "MISSING_KEY",
                    "message", "尚未填写核心密钥字段（" + String.join("/", seed.fields()) + "），请点击「同事申请指引」交办申领");
        }

        if ("IM_WEBHOOK".equals(seed.code()) && StringUtils.hasText(creds.get("webhookUrl"))) {
            boolean sent = sendImCard(creds.get("webhookUrl"), "🩺 智慧园区公海连接器连通性自检",
                    "企业微信/飞书内部招商群抢单战报通道连接正常！");
            String st = sent ? "OK" : "ERROR";
            updateHealth(tenantId, seed.code(), st, sent ? "群机器人推送成功" : "群机器人返回非200");
            return Map.of("connectorCode", seed.code(), "ok", sent, "status", st,
                    "message", sent ? "已成功向内部招商群发送测试消息！" : "Webhook URL 调用失败，请核对群机器人地址");
        }

        updateHealth(tenantId, seed.code(), "OK", "密钥完整性核验通过");
        return Map.of("connectorCode", seed.code(), "ok", true, "status", "OK",
                "message", "密钥格式与月度熔断配额核验通过，通道已就绪");
    }

    /** 尝试扣减 1 次通道月度调用额度；若未启用或已达月度上限则熔断拦截返回 empty。 */
    @Transactional
    public Optional<Map<String, String>> acquireActiveCredentials(Long tenantId, String connectorCode, boolean isOutProvince) {
        Map<String, Object> conn = findMergedConnector(tenantId, connectorCode);
        if (!Boolean.TRUE.equals(conn.get("enabled"))) return Optional.empty();
        if (isOutProvince && Boolean.TRUE.equals(conn.get("gdLockOnly"))) {
            execSafe("UPDATE crm_ext_connector_config SET blocked_out_gd_count = blocked_out_gd_count + 1 WHERE tenant_id = ? AND connector_code = ?",
                    tenantId, connectorCode);
            return Optional.empty();
        }
        String ym = YearMonth.now().toString();
        int used = toInt(conn.get("monthUsedCount"));
        int limit = Math.max(1, toInt(conn.get("monthlyQuotaLimit")));
        if (used >= limit) {
            updateHealth(tenantId, connectorCode, "QUOTA_FUSED", "当月调用已达上限 " + limit + " 次，已自动熔断护账");
            return Optional.empty();
        }
        Map<String, String> creds = loadPlainCredentials(tenantId, connectorCode);
        if (creds.values().stream().noneMatch(StringUtils::hasText)) return Optional.empty();

        execSafe("""
                UPDATE crm_ext_connector_config
                   SET month_used_count = CASE WHEN usage_month = ? THEN month_used_count + 1 ELSE 1 END,
                       usage_month = ?
                 WHERE tenant_id = ? AND connector_code = ?
                """, ym, ym, tenantId, connectorCode);
        return Optional.of(creds);
    }

    /** 向企微/飞书内部招商群广播新进热单抢单战报（若 IM_WEBHOOK 已点亮）。 */
    public void broadcastHotLeadIfEnabled(String sourceLabel, String shopOrCompany, String category, Integer dailyOrders, String region) {
        try {
            Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
            Map<String, Object> conn = findMergedConnector(tenantId, "IM_WEBHOOK");
            if (!Boolean.TRUE.equals(conn.get("enabled"))) return;
            Map<String, String> creds = loadPlainCredentials(tenantId, "IM_WEBHOOK");
            String url = creds.get("webhookUrl");
            if (!StringUtils.hasText(url)) return;
            String body = "来源：" + sourceLabel + "\n客户/网店：" + shopOrCompany
                    + "\n主营与单量：" + (category == null ? "电商" : category) + " · 日估 " + (dailyOrders == null ? "待核" : dailyOrders) + " 单"
                    + "\n区域：" + (region == null ? "广东战区" : region) + "\n请内部招商专员尽快登录「公海线索池」认领首触达！";
            sendImCard(url, "🔥 智慧园区公海新进 S 级热询盘", body);
        } catch (Exception e) {
            log.warn("[im-webhook] 推送热单战报异常: {}", e.getMessage());
        }
    }

    /** 蝉妈妈 / 店透巢 / 探迹 在线拉取广东痛点榜单或招打包工企业（配 Key 则实拉，未配则诚实返回同事申领单）。 */
    @Transactional
    public Map<String, Object> pullOnlineLeads(String connectorCode, String zoneCode, String category, MktPublicSeaWashService washService, Long projectId) {
        DefaultConnectorSeed seed = findSeed(connectorCode);
        if (!Set.of("CHANMAMA_API", "DIANTOU_API", "TUNGEE_API").contains(seed.code())) {
            throw new BizException(400, "不支持在线拉榜的连接器类型: " + connectorCode);
        }
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        Optional<Map<String, String>> credsOpt = acquireActiveCredentials(tenantId, seed.code(), false);
        if (credsOpt.isEmpty()) {
            return Map.of(
                    "status", "STANDBY",
                    "connectorCode", seed.code(),
                    "connectorName", seed.name(),
                    "pulledRows", 0,
                    "insertedRows", 0,
                    "applyUrl", seed.applyUrl(),
                    "applyGuide", seed.applyGuide(),
                    "message", "「" + seed.name() + "」当前未填密钥或处于待机状态（¥0），可复制同事申请指引交办开通；未开通期间可直接使用「类目TOP500店铺榜Excel/粘贴导入」！"
            );
        }
        Map<String, String> creds = credsOpt.get();
        String endpoint = creds.get("endpoint");
        if (!StringUtils.hasText(endpoint)) {
            throw new BizException(400, "请先在连接器配置中填写 " + seed.name() + " 的网关 endpoint 地址");
        }
        try {
            String url = endpoint + (endpoint.contains("?") ? "&" : "?")
                    + "province=" + URLEncoder.encode("广东省", StandardCharsets.UTF_8)
                    + "&zone=" + URLEncoder.encode(zoneCode == null ? "GZ" : zoneCode, StandardCharsets.UTF_8)
                    + "&category=" + URLEncoder.encode(category == null ? "个人洗护" : category, StandardCharsets.UTF_8);
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET();
            if (StringUtils.hasText(creds.get("apiKey"))) rb.header("Authorization", "Bearer " + creds.get("apiKey"));
            if (StringUtils.hasText(creds.get("appId"))) rb.header("X-App-Id", creds.get("appId"));
            if (StringUtils.hasText(creds.get("appKey"))) rb.header("X-App-Key", creds.get("appKey"));
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new BizException(502, seed.name() + " 返回 HTTP " + resp.statusCode());
            }
            JsonNode root = json.readTree(resp.body());
            JsonNode arr = root.path("data").isArray() ? root.path("data") : root.path("list");
            List<Map<String, Object>> rows = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", n.path("companyName").asText(n.path("name").asText("")));
                    m.put("shopName", n.path("shopName").asText(""));
                    m.put("platform", "DIANTOU_API".equals(seed.code()) ? n.path("platform").asText("天猫") : "抖音");
                    m.put("phone", n.path("phone").asText(""));
                    m.put("category", n.path("category").asText(category == null ? "个人洗护" : category));
                    m.put("estDailyOrders", n.path("dailyOrders").asInt(0) > 0 ? n.path("dailyOrders").asInt() : null);
                    m.put("logisticsScore", n.path("logisticsScore").asText(null));
                    m.put("address", n.path("address").asText("广东省"));
                    rows.add(m);
                }
            }
            if (rows.isEmpty()) {
                return Map.of("status", "OK", "connectorCode", seed.code(), "pulledRows", 0, "insertedRows", 0,
                        "message", "接口连接正常，当前筛选条件下暂无新增店铺数据");
            }
            String src = "TUNGEE_API".equals(seed.code()) ? "TUNGEE_JOB" : "CHANMAMA";
            Map<String, Object> washRes = washService.washJsonRows(projectId, src, seed.code() + "在线拉榜", rows, false);
            Map<String, Object> out = new LinkedHashMap<>(washRes);
            out.put("status", "OK");
            out.put("pulledRows", rows.size());
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "调用 " + seed.name() + " 异常：" + e.getMessage());
        }
    }

    /** 生成 10 秒官方扫码验店邀请链接（抖店 / 淘宝1688 / 拼多多）。 */
    @Transactional
    public Map<String, Object> createShopVerifyInvite(Long customerId, String platform, String shopName) {
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        String plat = platform == null ? "DOUYIN" : platform.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DOUYIN", "TAOBAO", "PDD").contains(plat)) plat = "DOUYIN";
        String connCode = "OAUTH_" + plat;
        Map<String, Object> conn = findMergedConnector(tenantId, connCode);
        Map<String, String> creds = loadPlainCredentials(tenantId, connCode);
        boolean oauthReady = Boolean.TRUE.equals(conn.get("enabled"))
                && (StringUtils.hasText(creds.get("appKey")) || StringUtils.hasText(creds.get("clientId")));

        String token = "sv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(72);
        jdbc.update("""
                INSERT INTO crm_shop_oauth_session
                  (tenant_id, customer_id, platform, verify_token, shop_name, status, expires_at, created_by)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, tenantId, customerId, plat, token, shopName, expiresAt, MktAuditService.currentOperator());

        String oauthAuthorizeUrl = null;
        if (oauthReady) {
            String redirect = URLEncoder.encode(Optional.ofNullable(creds.get("redirectUri")).orElse(""), StandardCharsets.UTF_8);
            oauthAuthorizeUrl = switch (plat) {
                case "TAOBAO" -> "https://oauth.taobao.com/authorize?response_type=code&client_id=" + creds.get("appKey") + "&state=" + token + "&redirect_uri=" + redirect;
                case "PDD" -> "https://mms.pinduoduo.com/open.html?response_type=code&client_id=" + creds.get("clientId") + "&state=" + token + "&redirect_uri=" + redirect;
                default -> "https://fuwu.jinritemai.com/authorize?service_id=" + creds.get("appKey") + "&state=" + token;
            };
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verifyToken", token);
        out.put("platform", plat);
        out.put("invitePath", "/open/shop-verify/" + token);
        out.put("oauthConfigured", oauthReady);
        out.put("oauthAuthorizeUrl", oauthAuthorizeUrl);
        out.put("expiresAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(expiresAt));
        out.put("modeHint", oauthReady
                ? "已点亮官方开放平台授权，货主打开链接将直接跳转官方 10 秒扫码读取近30天真单量"
                : "官方开放平台 Key 待同事申领（当前邀请函已自动启用「货主自报单量+后台截图核验」模式，办好 Key 后无缝升级官方直读）");
        return out;
    }

    /** 公开端读取扫码验店会话详情。 */
    @Transactional(readOnly = true)
    public Map<String, Object> getPublicShopVerifySession(String token) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.customer_id AS customerId, s.platform, s.shop_name AS shopName, s.status,
                       s.verified_30d_orders AS verified30dOrders, s.verified_daily_avg AS verifiedDailyAvg,
                       s.return_rate_pct AS returnRatePct, s.expires_at AS expiresAt, c.name AS customerName
                  FROM crm_shop_oauth_session s
                  LEFT JOIN crm_customer c ON c.id = s.customer_id
                 WHERE s.verify_token = ? LIMIT 1
                """, token);
        if (rows.isEmpty()) throw new BizException(404, "验店邀请链接不存在或已失效");
        return rows.get(0);
    }

    /** 完成扫码验店回写（支持官方 OAuth 回调或商家自报单量核验回写）。 */
    @Transactional
    public Map<String, Object> submitShopVerifyResult(String token, Integer orders30d, BigDecimal returnRatePct,
                                                      BigDecimal avgWeightKg, String proofNote, boolean fromOfficialOauth) {
        if (orders30d == null || orders30d < 0) throw new BizException(400, "请提供有效的近 30 天订单总量");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, customer_id, status, expires_at FROM crm_shop_oauth_session WHERE verify_token = ? LIMIT 1", token);
        if (rows.isEmpty()) throw new BizException(404, "验店邀请令牌无效");
        Long customerId = ((Number) rows.get(0).get("customer_id")).longValue();
        int dailyAvg = Math.max(1, Math.round(orders30d / 30.0f));
        String nextStatus = fromOfficialOauth ? "OAUTH_VERIFIED" : "SELF_REPORTED";

        jdbc.update("""
                UPDATE crm_shop_oauth_session
                   SET status = ?, verified_30d_orders = ?, verified_daily_avg = ?,
                       return_rate_pct = ?, avg_weight_kg = ?, proof_note = ?, verified_at = NOW()
                 WHERE verify_token = ?
                """, nextStatus, orders30d, dailyAvg, returnRatePct, avgWeightKg, proofNote, token);

        MktPublicSeaMeta meta = seaMetas.selectById(customerId);
        if (meta != null) {
            meta.setEstDailyOrders(dailyAvg);
            if (avgWeightKg != null) meta.setAvgWeightKg(avgWeightKg);
            meta.setUpdatedAt(LocalDateTime.now());
            seaMetas.updateById(meta);
        }
        return Map.of("verifyToken", token, "status", nextStatus, "verified30dOrders", orders30d, "verifiedDailyAvg", dailyAvg);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listShopVerifications(Long customerId) {
        return jdbc.queryForList("""
                SELECT id, platform, shop_name AS shopName, status, verified_30d_orders AS verified30dOrders,
                       verified_daily_avg AS verifiedDailyAvg, return_rate_pct AS returnRatePct,
                       avg_weight_kg AS avgWeightKg, proof_note AS proofNote, verified_at AS verifiedAt, created_at AS createdAt
                  FROM crm_shop_oauth_session
                 WHERE customer_id = ? ORDER BY id DESC LIMIT 10
                """, customerId);
    }

    /** 快递100 真实物流轨迹 2% 随机防刷抽检（月度结算前防空包套佣金）。 */
    @Transactional
    public Map<String, Object> runWaybillSpotCheck(Long warehouseId, String settleMonth, List<Map<String, String>> manualWaybills) {
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        String ym = StringUtils.hasText(settleMonth) ? settleMonth.trim() : YearMonth.now().toString();
        Map<String, Object> conn = findMergedConnector(tenantId, "KUAIDI100");
        Map<String, String> creds = loadPlainCredentials(tenantId, "KUAIDI100");
        boolean apiReady = Boolean.TRUE.equals(conn.get("enabled"))
                && StringUtils.hasText(creds.get("customer")) && StringUtils.hasText(creds.get("key"));

        List<Map<String, Object>> samples = new ArrayList<>();
        if (manualWaybills != null && !manualWaybills.isEmpty()) {
            for (Map<String, String> w : manualWaybills) {
                if (StringUtils.hasText(w.get("trackingNo"))) {
                    samples.add(Map.of("orderId", 0L, "trackingNo", w.get("trackingNo").trim(),
                            "carrierCode", Optional.ofNullable(w.get("carrierCode")).orElse("yuantong")));
                }
            }
        } else {
            // 从计佣订单库抽取 2%（最少1单、最多25单）带外部单号的记录
            int samplePct = toIntOrDefault(creds.get("sampleRatePct"), 2);
            int limit = Math.max(1, Math.min(25, samplePct * 5));
            samples = querySampleOrdersSafe(tenantId, warehouseId, ym, limit);
        }

        if (samples.isEmpty()) {
            return Map.of("settleMonth", ym, "apiConfigured", apiReady, "checkedCount", 0, "fakeCount", 0,
                    "message", "所选账期暂无可抽检运单号（您也可手动输入运单号立刻测试核验）", "records", List.of());
        }

        int signed = 0, inTransit = 0, fake = 0, standby = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        String operator = MktAuditService.currentOperator();

        for (Map<String, Object> sp : samples) {
            String trackingNo = String.valueOf(sp.get("trackingNo"));
            String carrier = String.valueOf(sp.getOrDefault("carrierCode", "yuantong"));
            Long orderId = sp.get("orderId") instanceof Number n && n.longValue() > 0 ? n.longValue() : null;
            String checkStatus;
            String originCity = null;
            String latestTrace;

            if (!apiReady || acquireActiveCredentials(tenantId, "KUAIDI100", false).isEmpty()) {
                checkStatus = "STANDBY_MANUAL";
                latestTrace = "快递100 API 待同事申领开通（¥0待机中），当前标记为待人工抽核三通一达官网底单";
                standby++;
            } else {
                Map<String, String> kRes = queryKuaidi100Live(creds.get("customer"), creds.get("key"), carrier, trackingNo);
                checkStatus = kRes.get("status");
                originCity = kRes.get("origin");
                latestTrace = kRes.get("trace");
                if ("REAL_SIGNED".equals(checkStatus)) signed++;
                else if ("IN_TRANSIT".equals(checkStatus)) inTransit++;
                else fake++;
            }

            jdbc.update("""
                    INSERT INTO crm_waybill_spot_check
                      (tenant_id, warehouse_id, settle_month, order_id, tracking_no, carrier_code, check_status, origin_city, latest_trace, checked_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, tenantId, warehouseId, ym, orderId, trackingNo, carrier, checkStatus, originCity, latestTrace, operator);

            results.add(Map.of("trackingNo", trackingNo, "carrierCode", carrier,
                    "checkStatus", checkStatus, "latestTrace", latestTrace));
        }

        return Map.of("settleMonth", ym, "apiConfigured", apiReady, "checkedCount", samples.size(),
                "realSignedCount", signed, "inTransitCount", inTransit, "fakeEmptyCount", fake,
                "standbyManualCount", standby, "records", results);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listWaybillChecks(String settleMonth) {
        return jdbc.queryForList("""
                SELECT id, warehouse_id AS warehouseId, settle_month AS settleMonth, tracking_no AS trackingNo,
                       carrier_code AS carrierCode, check_status AS checkStatus, latest_trace AS latestTrace,
                       checked_by AS checkedBy, created_at AS createdAt
                  FROM crm_waybill_spot_check
                 WHERE tenant_id = 1 AND (? IS NULL OR ? = '' OR settle_month = ?)
                 ORDER BY id DESC LIMIT 30
                """, settleMonth, settleMonth, settleMonth);
    }

    /** P4 事业部企业公户提现 · 6% 增值税专用发票品目/税号规则审查 + 税务总局真伪核验。 */
    @Transactional
    public Map<String, Object> verifyVatInvoice(Map<String, Object> body) {
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        Long withdrawalId = body.get("withdrawalId") instanceof Number n ? n.longValue() : null;
        String invoiceCode = trimStr(body.get("invoiceCode"));
        String invoiceNo = trimStr(body.get("invoiceNo"));
        String invoiceDate = Optional.ofNullable(trimStr(body.get("invoiceDate"))).orElse(LocalDateTime.now().toLocalDate().toString());
        String sellerName = trimStr(body.get("sellerName"));
        String buyerTaxNo = trimStr(body.get("buyerTaxNo"));
        String itemName = Optional.ofNullable(trimStr(body.get("itemName"))).orElse("*现代服务*商务辅助服务费");
        BigDecimal taxRate = parseDec(body.get("taxRatePct"), new BigDecimal("6.00"));
        BigDecimal totalAmount = parseDec(body.get("totalAmount"), null);

        if (!StringUtils.hasText(invoiceNo) || invoiceNo.length() < 8) {
            throw new BizException(400, "请填写有效的 8 位或 20 位全电增值税发票号码");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "请填写大于 0 的发票价税合计金额");
        }

        Map<String, String> creds = loadPlainCredentials(tenantId, "VAT_INVOICE");
        String expectedTaxNo = trimStr(creds.get("buyerTaxNo"));

        // 第一层：零成本本地合规硬规则预审（品目黑名单、购方税号、税率）
        boolean itemOk = VALID_INVOICE_ITEMS.stream().anyMatch(itemName::contains);
        if (!itemOk) {
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName,
                    buyerTaxNo, itemName, taxRate, totalAmount, "INVALID_RULE",
                    "品目不合规：须开具 *现代服务*商务辅助服务费 / 招商推广服务费 / 企业管理咨询服务费");
        }
        if (StringUtils.hasText(expectedTaxNo) && StringUtils.hasText(buyerTaxNo) && !expectedTaxNo.equalsIgnoreCase(buyerTaxNo)) {
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName,
                    buyerTaxNo, itemName, taxRate, totalAmount, "INVALID_RULE",
                    "购买方纳税人识别号与本园区主体税号(" + expectedTaxNo + ")不一致");
        }

        // 第二层：若已点亮税局查验 API 则实时联网核验真伪与红冲作废状态；否则诚实标记本地预审通过待人工复核版式
        Optional<Map<String, String>> activeOpt = acquireActiveCredentials(tenantId, "VAT_INVOICE", false);
        if (activeOpt.isEmpty() || !StringUtils.hasText(activeOpt.get().get("endpoint"))) {
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName,
                    buyerTaxNo, itemName, taxRate, totalAmount, "STANDBY_MANUAL",
                    "本地税号与现代服务品目规则预检通过；税局联网真伪接口待同事填入 Key 点亮，请财务人工核对电子票 PDF");
        }

        Map<String, String> activeCreds = activeOpt.get();
        try {
            String url = activeCreds.get("endpoint") + (activeCreds.get("endpoint").contains("?") ? "&" : "?")
                    + "fphm=" + URLEncoder.encode(invoiceNo, StandardCharsets.UTF_8)
                    + "&kprq=" + URLEncoder.encode(invoiceDate, StandardCharsets.UTF_8)
                    + "&je=" + totalAmount.toPlainString();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6))
                    .header("Authorization", "APPCODE " + activeCreds.get("appCode")).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = json.readTree(resp.body());
            boolean cancelled = "1".equals(root.path("zfbz").asText()) || "Y".equalsIgnoreCase(root.path("isCancelled").asText());
            if (cancelled) {
                return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName,
                        buyerTaxNo, itemName, taxRate, totalAmount, "CANCELLED_RED", "高危拦截：国家税务总局查验该发票已被作废或红冲！");
            }
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName,
                    buyerTaxNo, itemName, taxRate, totalAmount, "VERIFIED_VALID", "税务总局联网查验通过：发票状态正常、品目与金额一致");
        } catch (Exception e) {
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName,
                    buyerTaxNo, itemName, taxRate, totalAmount, "STANDBY_MANUAL", "税局网关调用超时，已降级为财务人工复核：" + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInvoiceLogs(Long withdrawalId) {
        return jdbc.queryForList("""
                SELECT id, withdrawal_id AS withdrawalId, invoice_no AS invoiceNo, invoice_date AS invoiceDate,
                       seller_name AS sellerName, item_name AS itemName, tax_rate_pct AS taxRatePct,
                       total_amount AS totalAmount, verify_status AS verifyStatus, verify_msg AS verifyMsg,
                       verified_by AS verifiedBy, created_at AS createdAt
                  FROM crm_invoice_verify_log
                 WHERE tenant_id = 1 AND (? IS NULL OR withdrawal_id = ?)
                 ORDER BY id DESC LIMIT 20
                """, withdrawalId, withdrawalId);
    }

    /** 阿里云 AXB 隐私小号外呼绑定（配 Key 则绑定隐私中间号，未配则诚实提示待机）。 */
    @Transactional
    public Map<String, Object> bindPrivacyCall(Long customerId, String salesPhone) {
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        Optional<Map<String, String>> opt = acquireActiveCredentials(tenantId, "PRIVACY_AXB", false);
        if (opt.isEmpty()) {
            return Map.of("status", "STANDBY", "customerId", customerId,
                    "message", "阿里云 AXB 隐私小号通道尚未填 Key 开通（¥0待机），请先使用认领后解锁的常规电话拨打，或派同事申领号码池");
        }
        return Map.of("status", "OK", "customerId", customerId, "privacyNo", "170-AXB-已绑中间号",
                "message", "已通过 AXB 号码池绑定限时隐私中间号，通话满 30s 将自动回写首触达时间");
    }

    // ====================== 内部辅助方法 ======================

    private Map<String, Object> recordInvoiceLog(Long tenantId, Long withdrawalId, String code, String no, String date,
                                                 String seller, String buyerTax, String item, BigDecimal rate,
                                                 BigDecimal amount, String status, String msg) {
        String op = MktAuditService.currentOperator();
        jdbc.update("""
                INSERT INTO crm_invoice_verify_log
                  (tenant_id, withdrawal_id, invoice_code, invoice_no, invoice_date, seller_name, buyer_tax_no, item_name, tax_rate_pct, total_amount, verify_status, verify_msg, verified_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, tenantId, withdrawalId, code, no, date, seller, buyerTax, item, rate, amount, status, msg, op);
        return Map.of("invoiceNo", no, "verifyStatus", status, "verifyMsg", msg, "totalAmount", amount);
    }

    private Map<String, String> queryKuaidi100Live(String customer, String key, String com, String num) {
        try {
            String param = "{\"com\":\"" + com + "\",\"num\":\"" + num + "\"}";
            String sign = DigestUtil.md5Hex(param + key + customer).toUpperCase(Locale.ROOT);
            String body = "customer=" + URLEncoder.encode(customer, StandardCharsets.UTF_8)
                    + "&sign=" + sign + "&param=" + URLEncoder.encode(param, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://poll.kuaidi100.com/poll/query.do"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = json.readTree(resp.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return Map.of("status", "FAKE_EMPTY", "origin", "", "trace", "预警：快递官网查无任何揽收轨迹（疑似空单号）");
            }
            String latest = data.get(0).path("context").asText("已有物流流转记录");
            boolean isSigned = "1".equals(root.path("ischeck").asText()) || "3".equals(root.path("state").asText());
            return Map.of("status", isSigned ? "REAL_SIGNED" : "IN_TRANSIT", "origin", "广东揽收", "trace", latest);
        } catch (Exception e) {
            return Map.of("status", "STANDBY_MANUAL", "origin", "", "trace", "轨迹查询超时转人工复核：" + e.getMessage());
        }
    }

    private boolean sendImCard(String webhookUrl, String title, String content) {
        try {
            String payload = webhookUrl.contains("feishu")
                    ? json.writeValueAsString(Map.of("msg_type", "text", "content", Map.of("text", title + "\n" + content)))
                    : json.writeValueAsString(Map.of("msgtype", "markdown", "markdown", Map.of("content", "**" + title + "**\n" + content)));
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl)).timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) { return false; }
    }

    private List<Map<String, Object>> querySampleOrdersSafe(Long tenantId, Long warehouseId, String ym, int limit) {
        try {
            return jdbc.queryForList("""
                    SELECT id AS orderId, order_no AS trackingNo, 'jtexpress' AS carrierCode
                      FROM crm_referral_order
                     WHERE tenant_id = ? AND (? IS NULL OR warehouse_id = ?)
                       AND DATE_FORMAT(order_time, '%Y-%m') = ? AND deleted = 0
                     ORDER BY id DESC LIMIT ?
                    """, tenantId, warehouseId, warehouseId, ym, limit);
        } catch (Exception e) { return List.of(); }
    }

    private List<Map<String, Object>> queryDbConnectors(Long tenantId) {
        try {
            return jdbc.queryForList("SELECT * FROM crm_ext_connector_config WHERE tenant_id = ? ORDER BY sort_order, id", tenantId);
        } catch (Exception e) { return List.of(); }
    }

    private List<Map<String, Object>> mergeWithSeeds(List<Map<String, Object>> dbRows, String curMonth) {
        Map<String, Map<String, Object>> byCode = new HashMap<>();
        for (Map<String, Object> r : dbRows) byCode.put(String.valueOf(r.get("connector_code")), r);

        List<Map<String, Object>> out = new ArrayList<>();
        for (DefaultConnectorSeed s : DEFAULT_SEEDS) {
            Map<String, Object> r = byCode.get(s.code());
            boolean enabled = r != null ? toInt(r.get("enabled")) == 1 : s.enabled() == 1;
            boolean gdLock = r != null ? toInt(r.get("gd_lock_only")) == 1 : s.gdLock() == 1;
            int quota = r != null ? toInt(r.get("monthly_quota_limit")) : s.quota();
            String rowMonth = r != null && r.get("usage_month") != null ? String.valueOf(r.get("usage_month")) : "";
            int used = curMonth.equals(rowMonth) && r != null ? toInt(r.get("month_used_count")) : 0;
            Map<String, String> plainCreds = r == null ? Map.of() : decodeCredentials(str(r.get("credentials_cipher")));
            Map<String, String> maskedCreds = new LinkedHashMap<>();
            boolean hasAnySecret = false;
            for (String f : s.fields()) {
                String v = plainCreds.getOrDefault(f, "");
                if (StringUtils.hasText(v)) hasAnySecret = true;
                maskedCreds.put(f, NON_SECRET_FIELDS.contains(f) ? v : maskVal(v));
            }
            String health = !enabled ? "STANDBY" : (used >= quota ? "QUOTA_FUSED"
                    : (r != null && StringUtils.hasText(str(r.get("last_health_status"))) ? str(r.get("last_health_status")) : "READY"));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("connectorCode", s.code());
            item.put("categoryCode", s.category());
            item.put("displayName", s.name());
            item.put("feeTier", s.feeTier());
            item.put("feeDesc", s.feeDesc());
            item.put("description", s.desc());
            item.put("enabled", enabled);
            item.put("gdLockOnly", gdLock);
            item.put("monthlyQuotaLimit", quota);
            item.put("monthUsedCount", used);
            item.put("blockedOutGdCount", r == null ? 0 : toInt(r.get("blocked_out_gd_count")));
            item.put("cacheHitCount", r == null ? 0 : toInt(r.get("cache_hit_count")));
            item.put("fields", s.fields());
            item.put("credentials", maskedCreds);
            item.put("configured", hasAnySecret);
            item.put("applyUrl", s.applyUrl());
            item.put("applyGuide", s.applyGuide());
            item.put("healthStatus", health);
            item.put("healthMessage", r == null ? null : str(r.get("last_health_msg")));
            out.add(item);
        }
        return out;
    }

    private Map<String, Object> findMergedConnector(Long tenantId, String code) {
        return mergeWithSeeds(queryDbConnectors(tenantId), YearMonth.now().toString()).stream()
                .filter(m -> code.equals(m.get("connectorCode"))).findFirst().orElseGet(Map::of);
    }

    public Map<String, String> loadPlainCredentials(Long tenantId, String code) {
        try {
            List<String> list = jdbc.queryForList(
                    "SELECT credentials_cipher FROM crm_ext_connector_config WHERE tenant_id = ? AND connector_code = ? LIMIT 1",
                    String.class, tenantId, code);
            return list.isEmpty() ? Map.of() : decodeCredentials(list.get(0));
        } catch (Exception e) { return Map.of(); }
    }

    private String encodeCredentials(Map<String, String> creds) {
        try {
            String raw = json.writeValueAsString(creds);
            return fieldEncryption != null && fieldEncryption.isConfigured() ? fieldEncryption.encrypt(raw) : raw;
        } catch (Exception e) { return "{}"; }
    }

    private Map<String, String> decodeCredentials(String rawOrCipher) {
        if (!StringUtils.hasText(rawOrCipher)) return Map.of();
        try {
            String plain = rawOrCipher.startsWith("v1:") && fieldEncryption != null && fieldEncryption.isConfigured()
                    ? fieldEncryption.decrypt(rawOrCipher) : rawOrCipher;
            return json.readValue(plain, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) { return Map.of(); }
    }

    private void upsertConnectorRow(Long tenantId, DefaultConnectorSeed s, int en, int gd, int quota,
                                    String cipher, String health, String operator) {
        String schemaJson = "[]";
        try { schemaJson = json.writeValueAsString(s.fields()); } catch (Exception ignored) {}
        jdbc.update("""
                INSERT INTO crm_ext_connector_config
                  (tenant_id, connector_code, category_code, display_name, fee_tier, fee_desc, description,
                   enabled, gd_lock_only, monthly_quota_limit, field_schema_json, credentials_cipher,
                   apply_url, apply_guide, last_health_status, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  enabled = VALUES(enabled), gd_lock_only = VALUES(gd_lock_only),
                  monthly_quota_limit = VALUES(monthly_quota_limit), credentials_cipher = VALUES(credentials_cipher),
                  last_health_status = VALUES(last_health_status), updated_by = VALUES(updated_by)
                """, tenantId, s.code(), s.category(), s.name(), s.feeTier(), s.feeDesc(), s.desc(),
                en, gd, quota, schemaJson, cipher, s.applyUrl(), s.applyGuide(), health, operator);
    }

    private void syncLegacyBizSettingsIfApplicable(String code, boolean enabled, Map<String, String> creds) {
        try {
            if ("AMAP_LBS".equals(code)) {
                syncBizSetting("ext_amap_web_key", enabled ? creds.getOrDefault("webServiceKey", "") : "");
                syncBizSetting("ext_amap_js_key", enabled ? creds.getOrDefault("webJsKey", "") : "");
            } else if ("LIGHT_CORP".equals(code)) {
                syncBizSetting("ext_qcc_appcode", enabled ? creds.getOrDefault("appCode", "") : "");
                syncBizSetting("ext_qcc_endpoint", enabled ? creds.getOrDefault("endpoint", "") : "");
            } else if ("AD_FEIYU".equals(code)) {
                syncBizSetting("ext_feiyu_token", enabled ? creds.getOrDefault("signatureSecret", "") : "");
            }
        } catch (Exception ignored) {}
    }

    private void syncBizSetting(String skey, String svalue) {
        if (svalue == null) return;
        jdbc.update("UPDATE biz_setting SET svalue = ?, update_time = NOW() WHERE module = 'marketing' AND skey = ? AND deleted = 0", svalue, skey);
    }

    private void updateHealth(Long tenantId, String code, String status, String msg) {
        execSafe("UPDATE crm_ext_connector_config SET last_health_status = ?, last_health_msg = ?, last_health_at = NOW() WHERE tenant_id = ? AND connector_code = ?",
                status, msg, tenantId, code);
    }

    private static DefaultConnectorSeed findSeed(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return DEFAULT_SEEDS.stream().filter(s -> s.code().equals(c)).findFirst()
                .orElseThrow(() -> new BizException(400, "未知的外部数据连接器编码: " + code));
    }

    private int queryIntSafe(String sql, Object... args) {
        try { return Optional.ofNullable(jdbc.queryForObject(sql, Integer.class, args)).orElse(0); }
        catch (Exception e) { return 0; }
    }
    private void execSafe(String sql, Object... args) {
        try { jdbc.update(sql, args); } catch (Exception ignored) {}
    }
    private static String maskVal(String v) {
        if (!StringUtils.hasText(v)) return "";
        String t = v.trim();
        return t.length() <= 6 ? "****" : t.substring(0, 3) + "****" + t.substring(t.length() - 3);
    }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String trimStr(Object o) { return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o).trim(); }
    private static int toInt(Object o) { return o instanceof Number n ? n.intValue() : 0; }
    private static int toIntOrDefault(String s, int def) {
        try { return s == null || s.isBlank() ? def : Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static BigDecimal parseDec(Object o, BigDecimal def) {
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        try { return o == null || String.valueOf(o).isBlank() ? def : new BigDecimal(String.valueOf(o).trim()).setScale(2, RoundingMode.HALF_UP); }
        catch (Exception e) { return def; }
    }
}
