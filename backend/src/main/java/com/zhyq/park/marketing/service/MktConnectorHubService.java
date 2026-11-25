package com.zhyq.park.marketing.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
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
 * 外部数据连接器中心：
 * - 未开启或未填密钥的通道不发起任何外部请求（¥0 待机），也绝不编造核验结果；
 * - 付费通道每次调用前原子扣减当月额度，达上限自动熔断；
 * - 密钥只存这一张表，且必须在服务器已配置字段加密密钥时才允许保存；
 * - readiness 如实标注每条通道的完成程度：READY（按官方文档实现，待真 Key 联调）/ ADAPT（通用接法，需按对方文档调整）/ DEV（未完成）。
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
    private final MktAuditService audit;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public record DefaultConnectorSeed(
            String code, String category, String name, String feeTier, String feeDesc,
            String desc, int enabled, int gdLock, int quota, List<String> fields,
            String applyUrl, String applyGuide, String readiness) {}

    public static final List<DefaultConnectorSeed> DEFAULT_SEEDS = List.of(
            new DefaultConnectorSeed("QCC_PRO", "CORP", "企查查官方开放平台（工商+失信+名下网店）", "PAID_CALL", "商业按次 · 约¥0.15~0.30/次",
                    "穿透统一信用代码、法人、参保人数、失信限高及名下电商店铺，失信主体标红禁开月结。", 0, 1, 2000,
                    List.of("appKey", "secretKey"), "https://openapi.qcc.com", "用园区营业执照认证企查查开放平台 → 申请企业工商照面+失信被执行+网店接口 → 拿回 appKey 与 secretKey", "READY"),
            new DefaultConnectorSeed("TYC_PRO", "CORP", "天眼查开放平台（工商与诉讼备用源）", "PAID_CALL", "商业按次/年包",
                    "企查查未开通或异常时的备用工商数据源，支持企业检索与司法风险。", 0, 1, 1000,
                    List.of("token"), "https://open.tianyancha.com", "企业实名注册天眼查开放平台 → 开通工商基本信息接口 → 复制 Authorization token", "ADAPT"),
            new DefaultConnectorSeed("LIGHT_CORP", "CORP", "阿里云云市场 / APISpace 百元轻量工商包", "LOW_PACK", "百元按次 · ¥50~199",
                    "未签企查查前的过渡方案，补齐执照法人与年报电话；不同商品返回格式不同，接入时需核对一次。", 0, 1, 1000,
                    List.of("appCode", "endpoint"), "https://market.aliyun.com", "阿里云市场搜「企业工商信息查询」买按次包 → 复制 appCode 与请求地址 endpoint", "ADAPT"),
            new DefaultConnectorSeed("TUNGEE_API", "CORP", "探迹拓客 OpenAPI（关键人手机+招打包工企业）", "PAID_SAAS", "商业SaaS/API",
                    "补全电商老板/供应链负责人手机号，并拉取广东新发招打包工企业；需按探迹合同接口文档适配。", 0, 1, 1500,
                    List.of("appKey", "appSecret", "endpoint"), "https://open.tungee.com", "联系探迹客户经理开通 OpenAPI → 拿回 appKey、appSecret 与 endpoint", "ADAPT"),
            new DefaultConnectorSeed("CHANMAMA_API", "ECOM", "蝉妈妈数据 API（抖音低物流分店铺榜）", "PAID_SAAS", "商业数据API",
                    "按广东发货地+类目拉取物流体验分偏低的热销抖店；蝉妈妈无公开标准接口，需按商务给的文档适配。", 0, 1, 1000,
                    List.of("appId", "appSecret", "endpoint"), "https://www.chanmama.com", "联系蝉妈妈商务开通企业数据 API → 获取 appId、appSecret 与接口地址", "ADAPT"),
            new DefaultConnectorSeed("DIANTOU_API", "ECOM", "店透巢 / 电霸 API（淘天/拼多多/1688 广东货盘）", "PAID_SAAS", "商业数据API",
                    "筛选广东发货的天猫淘宝、拼多多与1688店铺销量和物流评分；需按对方文档适配。", 0, 1, 1000,
                    List.of("apiKey", "endpoint"), "https://www.diantouchao.com", "开通店透巢或电霸企业 API → 获取 apiKey 与接口地址", "ADAPT"),
            new DefaultConnectorSeed("OAUTH_DOUYIN", "ECOM", "抖店开放平台（官方授权读真实单量）", "FREE", "¥0官方接口 · 需服务商资质",
                    "当前可用：生成验店邀请函、货主自报单量（标注未核验）。官方授权回调与读单【开发中】，需先取得服务商资质。", 0, 0, 5000,
                    List.of("appKey", "appSecret", "redirectUri"), "https://op.jinritemai.com", "用园区科技主体入驻抖店开放平台并按仓储服务场景申请 → 拿回 appKey、appSecret 与回调地址", "DEV"),
            new DefaultConnectorSeed("OAUTH_TAOBAO", "ECOM", "淘宝天猫 & 1688 开放平台（官方授权读单）", "PAID_SAAS", "官方接口 · 需开放平台认证",
                    "当前可用：验店邀请函与货主自报。官方授权读单【开发中】。", 0, 0, 5000,
                    List.of("appKey", "appSecret", "redirectUri"), "https://open.taobao.com", "注册淘宝/1688 开放平台企业开发者并申请交易汇总只读权限 → 拿回 appKey 与 appSecret", "DEV"),
            new DefaultConnectorSeed("OAUTH_PDD", "ECOM", "拼多多开放平台（官方授权读单）", "FREE", "¥0官方接口 · 需开发者审核",
                    "当前可用：验店邀请函与货主自报。官方授权读单【开发中】。", 0, 0, 5000,
                    List.of("clientId", "clientSecret", "redirectUri"), "https://open.pinduoduo.com", "注册拼多多开放平台企业开发者 → 获取 clientId 与 clientSecret", "DEV"),
            new DefaultConnectorSeed("AMAP_LBS", "LBS", "高德开放平台（地址落图+周边雷达+地图）", "FREE", "¥0企业免费配额",
                    "广东地址转坐标、周边商圈扫楼雷达、公海地图；客户落图后才能做 500 米打卡。", 0, 0, 150000,
                    List.of("webServiceKey", "webJsKey", "securityJsCode"), "https://lbs.amap.com", "高德开放平台企业认证 → 分别创建「Web服务 Key」与「Web端 JS API Key」", "READY"),
            new DefaultConnectorSeed("AD_FEIYU", "AD", "巨量引擎「飞鱼」广告线索回调（抖音）", "FREE", "¥0免费推送",
                    "抖音广告表单实时推入公海；未填验签密钥时一律拒收，防止被灌假线索。验签算法需按飞鱼文档核对一次。", 0, 0, 999999,
                    List.of("signatureSecret"), "https://feiyu.oceanengine.com", "飞鱼CRM › 设置 › 线索推送填 /api/open/v1/ad-leads/feiyu → 把签名密钥填回 signatureSecret", "ADAPT"),
            new DefaultConnectorSeed("AD_TENCENT", "AD", "腾讯广告「线索通」回调（朋友圈/视频号）", "FREE", "¥0免费推送",
                    "朋友圈、视频号广告表单实时推入公海；未填密钥一律拒收。", 0, 0, 999999,
                    List.of("tokenSecret"), "https://e.qq.com", "线索通配置回调 /api/open/v1/ad-leads/tencent → 把 Token 填回 tokenSecret", "ADAPT"),
            new DefaultConnectorSeed("AD_XHS", "AD", "小红书「聚光」线索回调", "FREE", "¥0接口费",
                    "小红书品牌找仓表单实时推入公海；未填密钥一律拒收。", 0, 0, 999999,
                    List.of("secret"), "https://ad.xiaohongshu.com", "聚光平台 › 线索管理 › 回调填 /api/open/v1/ad-leads/xhs → 把密钥填回 secret", "ADAPT"),
            new DefaultConnectorSeed("AD_BAIDU", "AD", "百度「基木鱼」搜索线索回调", "FREE", "¥0接口费",
                    "搜索找仓询盘实时推入公海；未填密钥一律拒收。", 0, 0, 999999,
                    List.of("token"), "https://yingxiao.baidu.com", "基木鱼配置线索推送 /api/open/v1/ad-leads/baidu → 把 Token 填回 token", "ADAPT"),
            new DefaultConnectorSeed("KUAIDI100", "LOGI", "快递100 物流轨迹抽检（防空包刷佣金）", "PAID_CALL", "按单约¥0.03",
                    "月度结算前抽查运单是否有真实揽收签收轨迹；抽样取数需按实际出库单表核对。", 0, 0, 3000,
                    List.of("customer", "key", "sampleRatePct"), "https://api.kuaidi100.com", "注册快递100企业API → 充值体验包 → 复制 customer 编号与 key", "READY"),
            new DefaultConnectorSeed("IM_WEBHOOK", "CALL", "企业微信 / 飞书招商群抢单机器人", "FREE", "¥0完全免费",
                    "新热单入池 3 秒内推送到内部招商群。", 0, 0, 999999,
                    List.of("webhookUrl"), "企业微信/飞书群设置", "在内部招商群点「添加群机器人」→ 复制完整 webhookUrl 粘贴即可", "READY"),
            new DefaultConnectorSeed("PRIVACY_AXB", "CALL", "阿里云 AXB 隐私小号外呼", "PAID_CALL", "通信费约¥0.06/分钟",
                    "保护客户号码、防外呼封卡。【接口对接开发中，暂不可用】", 0, 0, 5000,
                    List.of("accessKeyId", "accessKeySecret", "poolKey", "endpoint"), "https://dypls.console.aliyun.com", "阿里云号码隐私保护控制台开通 AXB 号码池 → 复制 accessKeyId、accessKeySecret 与 poolKey", "DEV"),
            new DefaultConnectorSeed("VAT_INVOICE", "FIN", "6% 增值税专票真伪查验", "PAID_CALL", "按张约¥0.15",
                    "本地先审品目与购方税号；接通查验接口后再查红冲作废。查验接口参数需按所购商品文档核对。", 0, 0, 500,
                    List.of("appCode", "endpoint", "buyerTaxNo"), "https://market.aliyun.com", "阿里云市场搜「增值税发票查验」买按次包 → 复制 appCode、endpoint，并填本园区税号 buyerTaxNo", "ADAPT")
    );

    /** 18 条连接器脱敏配置 + 顶部 3 卡同源统计。 */
    @Transactional(readOnly = true)
    public Map<String, Object> hubOverview() {
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        List<Map<String, Object>> connectors = mergeWithSeeds(queryDbConnectors(tenantId), YearMonth.now().toString());

        int activeCount = 0, freeCount = 0, paidCount = 0, fusedCount = 0;
        int totalMonthUsed = 0, totalBlockedOutGd = 0;
        for (Map<String, Object> c : connectors) {
            if (Boolean.TRUE.equals(c.get("enabled"))) activeCount++;
            if ("FREE".equals(c.get("feeTier"))) freeCount++; else paidCount++;
            if ("QUOTA_FUSED".equals(c.get("healthStatus"))) fusedCount++;
            totalMonthUsed += toInt(c.get("monthUsedCount"));
            totalBlockedOutGd += toInt(c.get("blockedOutGdCount"));
        }
        int batchOutGd = queryIntSafe("SELECT COALESCE(SUM(skipped_out_province),0) FROM crm_public_sea_wash_batch WHERE tenant_id = ?", tenantId);
        int batchCache = queryIntSafe("SELECT COALESCE(SUM(qcc_cache_hits),0) FROM crm_public_sea_wash_batch WHERE tenant_id = ?", tenantId);
        int combinedOutGd = totalBlockedOutGd + batchOutGd;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fetchedAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
        summary.put("totalConnectors", connectors.size());
        summary.put("activeConnectors", activeCount);
        summary.put("freeConnectors", freeCount);
        summary.put("paidConnectors", paidCount);
        summary.put("fusedConnectors", fusedCount);
        summary.put("monthUsedTotal", totalMonthUsed);
        summary.put("blockedOutGdTotal", combinedOutGd);
        summary.put("cacheHitTotal", batchCache);
        // 估算口径：按商业接口均价 0.20 元/次折算，界面须标注"估算"
        summary.put("estimatedSavedYuan", BigDecimal.valueOf(combinedOutGd + batchCache)
                .multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP));
        summary.put("adInboxTotal", queryIntSafe("SELECT COUNT(1) FROM crm_ext_ad_lead_inbox WHERE tenant_id = ?", tenantId));
        summary.put("shopVerifiedCount", queryIntSafe("SELECT COUNT(1) FROM crm_shop_oauth_session WHERE tenant_id = ? AND status IN ('OAUTH_VERIFIED','SELF_REPORTED')", tenantId));
        summary.put("waybillCheckedCount", queryIntSafe("SELECT COUNT(1) FROM crm_waybill_spot_check WHERE tenant_id = ?", tenantId));
        summary.put("waybillFakeCount", queryIntSafe("SELECT COUNT(1) FROM crm_waybill_spot_check WHERE tenant_id = ? AND check_status = 'FAKE_EMPTY'", tenantId));
        summary.put("invoiceVerifiedCount", queryIntSafe("SELECT COUNT(1) FROM crm_invoice_verify_log WHERE tenant_id = ? AND verify_status = 'VERIFIED_VALID'", tenantId));
        summary.put("encryptionReady", fieldEncryption != null && fieldEncryption.isConfigured());
        return Map.of("summary", summary, "connectors", connectors);
    }

    /** 保存连接器开关、省钱锁、月度上限与密钥（含 **** 的字段保持原值）。 */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveConnector(String connectorCode, Map<String, Object> body) {
        DefaultConnectorSeed seed = findSeed(connectorCode);
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        boolean enabled = Boolean.TRUE.equals(body.get("enabled")) || "1".equals(String.valueOf(body.get("enabled")));
        boolean gdLockOnly = body.get("gdLockOnly") == null ? (seed.gdLock() == 1) : Boolean.TRUE.equals(body.get("gdLockOnly"));
        int quota = body.get("monthlyQuotaLimit") instanceof Number n ? Math.max(10, Math.min(9_999_999, n.intValue())) : seed.quota();

        Map<String, String> merged = new LinkedHashMap<>(loadPlainCredentials(tenantId, seed.code()));
        Map<String, Object> inputCreds = body.get("credentials") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        for (String field : seed.fields()) {
            if (inputCreds.containsKey(field)) {
                String val = inputCreds.get(field) == null ? "" : String.valueOf(inputCreds.get(field)).trim();
                if (!val.contains("****")) merged.put(field, val);
            }
        }
        boolean hasSecret = coreSecretPresent(merged);
        if (hasSecret && (fieldEncryption == null || !fieldEncryption.isConfigured())) {
            throw new BizException(400, "服务器未配置字段加密密钥（ZHYQ_FIELD_ENCRYPTION_KEY），为防密钥明文入库暂不允许保存；请先由运维配置后再填写");
        }
        String health = !enabled ? "STANDBY" : (hasSecret ? ("DEV".equals(seed.readiness()) ? "DEV_PENDING" : "READY") : "MISSING_KEY");
        upsertConnectorRow(tenantId, seed, enabled ? 1 : 0, gdLockOnly ? 1 : 0, quota, encodeCredentials(merged), health,
                MktAuditService.currentOperator());
        audit.log("connector.save", "connector", null,
                seed.code() + " enabled=" + enabled + " quota=" + quota + " gdLock=" + gdLockOnly + " secretSet=" + hasSecret);
        return Map.of("connectorCode", seed.code(), "enabled", enabled, "healthStatus", health, "monthlyQuotaLimit", quota);
    }

    /** 连通性自检：不发起扣费请求，只核对开关、密钥与完成程度（群机器人会真实发一条测试消息）。 */
    @Transactional
    public Map<String, Object> pingConnector(String connectorCode) {
        DefaultConnectorSeed seed = findSeed(connectorCode);
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        Map<String, Object> row = queryConnectorRow(tenantId, seed.code());
        boolean enabled = row != null && toInt(row.get("enabled")) == 1;
        if (!enabled) {
            return Map.of("connectorCode", seed.code(), "ok", false, "status", "STANDBY",
                    "message", "该通道处于关闭状态（¥0），派同事申领 Key 填入并开启后再自检");
        }
        Map<String, String> creds = decodeCredentials(str(row.get("credentials_cipher")));
        if (!coreSecretPresent(creds)) {
            updateHealth(tenantId, seed.code(), "MISSING_KEY", "已开启但未填密钥");
            String extra = seed.code().startsWith("AD_") ? "；未填验签密钥时回调会被拒收" : "";
            return Map.of("connectorCode", seed.code(), "ok", false, "status", "MISSING_KEY",
                    "message", "尚未填写密钥字段（" + String.join("/", seed.fields()) + "）" + extra);
        }
        if ("DEV".equals(seed.readiness())) {
            updateHealth(tenantId, seed.code(), "DEV_PENDING", "密钥已保存，接口对接开发中");
            return Map.of("connectorCode", seed.code(), "ok", false, "status", "DEV_PENDING",
                    "message", "密钥已保存，但该通道的官方接口对接尚未开发完成，暂不可用");
        }
        if ("IM_WEBHOOK".equals(seed.code())) {
            boolean sent = sendImCard(creds.get("webhookUrl"), "智慧园区公海连接器自检", "招商群抢单机器人连接正常。");
            updateHealth(tenantId, seed.code(), sent ? "OK" : "ERROR", sent ? "测试消息发送成功" : "群机器人返回非200");
            return Map.of("connectorCode", seed.code(), "ok", sent, "status", sent ? "OK" : "ERROR",
                    "message", sent ? "已向招商群发送测试消息" : "群机器人地址调用失败，请核对 webhookUrl");
        }
        String note = "ADAPT".equals(seed.readiness()) ? "；该通道为通用接法，首次拿到真实 Key 时需按对方文档联调一次" : "";
        updateHealth(tenantId, seed.code(), "READY", "密钥已填写");
        return Map.of("connectorCode", seed.code(), "ok", true, "status", "READY",
                "message", "开关与密钥已就绪（自检未发起扣费请求，真实可用性以首次调用结果为准）" + note);
    }

    /**
     * 付费通道取用凭证并原子扣减 1 次当月额度：
     * 未开启/未填密钥/外省被锁/当月已满 → 返回 empty，不发外部请求。
     */
    @Transactional
    public Optional<Map<String, String>> acquireActiveCredentials(Long tenantId, String connectorCode, boolean isOutProvince) {
        Map<String, Object> row = queryConnectorRow(tenantId, connectorCode);
        if (row == null || toInt(row.get("enabled")) != 1) return Optional.empty();
        if (isOutProvince && toInt(row.get("gd_lock_only")) == 1) {
            execSafe("UPDATE crm_ext_connector_config SET blocked_out_gd_count = blocked_out_gd_count + 1 WHERE tenant_id = ? AND connector_code = ?",
                    tenantId, connectorCode);
            return Optional.empty();
        }
        Map<String, String> creds = decodeCredentials(str(row.get("credentials_cipher")));
        if (!coreSecretPresent(creds)) return Optional.empty();
        String ym = YearMonth.now().toString();
        int changed = jdbc.update("""
                UPDATE crm_ext_connector_config
                   SET month_used_count = CASE WHEN usage_month = ? THEN month_used_count + 1 ELSE 1 END,
                       usage_month = ?
                 WHERE tenant_id = ? AND connector_code = ? AND enabled = 1
                   AND (usage_month <> ? OR month_used_count < monthly_quota_limit)
                """, ym, ym, tenantId, connectorCode, ym);
        if (changed != 1) {
            updateHealth(tenantId, connectorCode, "QUOTA_FUSED", "当月调用已达上限，已自动熔断");
            return Optional.empty();
        }
        return Optional.of(creds);
    }

    /** 免费通道（高德、广告回调、群机器人）取用凭证：只看开关与密钥，不计额度。 */
    public Map<String, String> activeCredentials(Long tenantId, String connectorCode) {
        Map<String, Object> row = queryConnectorRow(tenantId, connectorCode);
        if (row == null || toInt(row.get("enabled")) != 1) return Map.of();
        return decodeCredentials(str(row.get("credentials_cipher")));
    }

    /** 新热单入池后推送企微/飞书群（未开启则静默跳过）。 */
    public void broadcastHotLeadIfEnabled(String sourceLabel, String shopOrCompany, String category, Integer dailyOrders, String region) {
        try {
            String url = activeCredentials(MyMetaObjectHandler.DEFAULT_TENANT_ID, "IM_WEBHOOK").get("webhookUrl");
            if (!StringUtils.hasText(url)) return;
            String body = "来源：" + sourceLabel + "\n客户/网店：" + shopOrCompany
                    + "\n主营与单量：" + (category == null ? "电商" : category) + " · 日估 " + (dailyOrders == null ? "待核" : dailyOrders) + " 单"
                    + "\n区域：" + (region == null ? "待核" : region) + "\n请尽快到「公海线索池」认领跟进。";
            sendImCard(url, "公海新进热询盘", body);
        } catch (Exception e) {
            log.warn("[im-webhook] 推送热单异常: {}", e.getMessage());
        }
    }

    /** 蝉妈妈 / 店透巢 / 探迹 在线拉榜（未开通则如实返回申领指引）。 */
    @Transactional
    public Map<String, Object> pullOnlineLeads(String connectorCode, String zoneCode, String category, MktPublicSeaWashService washService, Long projectId) {
        DefaultConnectorSeed seed = findSeed(connectorCode);
        if (!Set.of("CHANMAMA_API", "DIANTOU_API", "TUNGEE_API").contains(seed.code())) {
            throw new BizException(400, "不支持在线拉榜的连接器类型: " + connectorCode);
        }
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        Optional<Map<String, String>> credsOpt = acquireActiveCredentials(tenantId, seed.code(), false);
        if (credsOpt.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "STANDBY");
            out.put("connectorCode", seed.code());
            out.put("connectorName", seed.name());
            out.put("pulledRows", 0);
            out.put("insertedRows", 0);
            out.put("applyUrl", seed.applyUrl());
            out.put("applyGuide", seed.applyGuide());
            out.put("message", "「" + seed.name() + "」未开通或本月额度已用完（¥0 待机）。未开通期间可用「类目店铺榜 Excel/粘贴导入」。");
            return out;
        }
        Map<String, String> creds = credsOpt.get();
        String endpoint = creds.get("endpoint");
        if (!StringUtils.hasText(endpoint)) throw new BizException(400, "请先在连接器中心填写 " + seed.name() + " 的接口地址 endpoint");
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
            if (resp.statusCode() != 200) throw new BizException(502, seed.name() + " 返回 HTTP " + resp.statusCode());
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
                    m.put("address", n.path("address").asText(""));
                    rows.add(m);
                }
            }
            if (rows.isEmpty()) {
                return Map.of("status", "OK", "connectorCode", seed.code(), "pulledRows", 0, "insertedRows", 0,
                        "message", "接口返回为空：当前条件下无数据，或返回格式与通用接法不一致需按对方文档适配");
            }
            String src = "TUNGEE_API".equals(seed.code()) ? "TUNGEE_JOB" : "CHANMAMA";
            Map<String, Object> out = new LinkedHashMap<>(washService.washJsonRows(projectId, src, seed.code() + "在线拉榜", rows, false));
            out.put("status", "OK");
            out.put("pulledRows", rows.size());
            return out;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "调用 " + seed.name() + " 异常：" + e.getMessage());
        }
    }

    /** 生成验店邀请函（官方授权读单未开发，当前只支持货主自报，结果标注"未核验"）。 */
    @Transactional
    public Map<String, Object> createShopVerifyInvite(Long customerId, String platform, String shopName) {
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        String plat = platform == null ? "DOUYIN" : platform.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DOUYIN", "TAOBAO", "PDD").contains(plat)) plat = "DOUYIN";
        String token = "sv_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(72);
        jdbc.update("""
                INSERT INTO crm_shop_oauth_session
                  (tenant_id, customer_id, platform, verify_token, shop_name, status, expires_at, created_by)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, tenantId, customerId, plat, token, shopName, expiresAt, MktAuditService.currentOperator());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verifyToken", token);
        out.put("platform", plat);
        out.put("invitePath", "/open/shop-verify/" + token);
        out.put("oauthConfigured", false);
        out.put("expiresAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(expiresAt));
        out.put("modeHint", "当前为货主自报模式（结果标注「自报未核验」）；官方授权读单尚在开发中，72 小时内有效且只能提交一次");
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicShopVerifySession(String token) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.platform, s.shop_name AS shopName, s.status, s.expires_at AS expiresAt
                  FROM crm_shop_oauth_session s
                 WHERE s.verify_token = ? LIMIT 1
                """, token);
        if (rows.isEmpty()) throw new BizException(404, "验店邀请链接不存在或已失效");
        Map<String, Object> out = new LinkedHashMap<>(rows.get(0));
        Object exp = out.get("expiresAt");
        boolean expired = exp != null && toLdt(exp).isBefore(LocalDateTime.now());
        out.put("expired", expired);
        out.put("submittable", !expired && "PENDING".equals(String.valueOf(out.get("status"))));
        return out;
    }

    /** 公开端提交：只能写入「货主自报」，校验未过期、仅可提交一次；不回写客户画像，由专员人工采纳。 */
    @Transactional
    public Map<String, Object> submitShopVerifyResult(String token, Integer orders30d, BigDecimal returnRatePct,
                                                      BigDecimal avgWeightKg, String proofNote) {
        if (orders30d == null || orders30d < 0 || orders30d > 10_000_000) throw new BizException(400, "请提供有效的近 30 天订单总量");
        int dailyAvg = Math.max(1, Math.round(orders30d / 30.0f));
        String note = proofNote == null ? "货主自报" : (proofNote.length() > 200 ? proofNote.substring(0, 200) : proofNote);
        int updated = jdbc.update("""
                UPDATE crm_shop_oauth_session
                   SET status = 'SELF_REPORTED', verified_30d_orders = ?, verified_daily_avg = ?,
                       return_rate_pct = ?, avg_weight_kg = ?, proof_note = ?, verified_at = NOW()
                 WHERE verify_token = ? AND status = 'PENDING' AND expires_at > NOW()
                """, orders30d, dailyAvg, returnRatePct, avgWeightKg, note, token);
        if (updated != 1) throw new BizException(409, "验店链接已过期、已提交过或不存在");
        return Map.of("verifyToken", token, "status", "SELF_REPORTED", "verified30dOrders", orders30d, "verifiedDailyAvg", dailyAvg);
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

    /** 快递100 运单轨迹抽检（未开通时标记为待人工核对）。 */
    @Transactional
    public Map<String, Object> runWaybillSpotCheck(Long warehouseId, String settleMonth, List<Map<String, String>> manualWaybills) {
        Long tenantId = MyMetaObjectHandler.DEFAULT_TENANT_ID;
        String ym = StringUtils.hasText(settleMonth) ? settleMonth.trim() : YearMonth.now().toString();
        Map<String, String> plain = activeCredentials(tenantId, "KUAIDI100");
        boolean apiReady = StringUtils.hasText(plain.get("customer")) && StringUtils.hasText(plain.get("key"));

        List<Map<String, Object>> samples = new ArrayList<>();
        if (manualWaybills != null && !manualWaybills.isEmpty()) {
            for (Map<String, String> w : manualWaybills) {
                if (StringUtils.hasText(w.get("trackingNo"))) {
                    samples.add(Map.of("orderId", 0L, "trackingNo", w.get("trackingNo").trim(),
                            "carrierCode", Optional.ofNullable(w.get("carrierCode")).orElse("yuantong")));
                }
            }
        } else {
            int samplePct = toIntOrDefault(plain.get("sampleRatePct"), 2);
            samples = querySampleOrdersSafe(tenantId, warehouseId, ym, Math.max(1, Math.min(25, samplePct * 5)));
        }
        if (samples.isEmpty()) {
            return Map.of("settleMonth", ym, "apiConfigured", apiReady, "checkedCount", 0, "fakeEmptyCount", 0,
                    "message", "所选账期暂无可抽检运单号（可手动输入运单号核验）", "records", List.of());
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
            Optional<Map<String, String>> creds = apiReady ? acquireActiveCredentials(tenantId, "KUAIDI100", false) : Optional.empty();
            if (creds.isEmpty()) {
                checkStatus = "STANDBY_MANUAL";
                latestTrace = apiReady ? "快递100 本月额度已用完，转人工核对底单" : "快递100 未开通（¥0待机），转人工核对底单";
                standby++;
            } else {
                Map<String, String> kRes = queryKuaidi100Live(creds.get().get("customer"), creds.get().get("key"), carrier, trackingNo);
                checkStatus = kRes.get("status");
                originCity = kRes.get("origin");
                latestTrace = kRes.get("trace");
                if ("REAL_SIGNED".equals(checkStatus)) signed++;
                else if ("IN_TRANSIT".equals(checkStatus)) inTransit++;
                else if ("FAKE_EMPTY".equals(checkStatus)) fake++;
                else standby++;
            }
            jdbc.update("""
                    INSERT INTO crm_waybill_spot_check
                      (tenant_id, warehouse_id, settle_month, order_id, tracking_no, carrier_code, check_status, origin_city, latest_trace, checked_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, tenantId, warehouseId, ym, orderId, trackingNo, carrier, checkStatus, originCity, latestTrace, operator);
            results.add(Map.of("trackingNo", trackingNo, "carrierCode", carrier, "checkStatus", checkStatus, "latestTrace", latestTrace));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settleMonth", ym);
        out.put("apiConfigured", apiReady);
        out.put("checkedCount", samples.size());
        out.put("realSignedCount", signed);
        out.put("inTransitCount", inTransit);
        out.put("fakeEmptyCount", fake);
        out.put("standbyManualCount", standby);
        out.put("records", results);
        return out;
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

    /** 6% 专票：本地品目/税号硬规则预审 + （已开通时）查验接口查红冲作废。 */
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
        if (!StringUtils.hasText(invoiceNo) || invoiceNo.length() < 8) throw new BizException(400, "请填写有效的 8 位或 20 位发票号码");
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) throw new BizException(400, "请填写大于 0 的价税合计金额");

        String expectedTaxNo = trimStr(loadPlainCredentials(tenantId, "VAT_INVOICE").get("buyerTaxNo"));
        if (VALID_INVOICE_ITEMS.stream().noneMatch(itemName::contains)) {
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName, buyerTaxNo, itemName,
                    taxRate, totalAmount, "INVALID_RULE", "品目不合规：须开具 *现代服务*商务辅助服务费 / 招商推广服务费 / 企业管理咨询服务费");
        }
        if (StringUtils.hasText(expectedTaxNo) && StringUtils.hasText(buyerTaxNo) && !expectedTaxNo.equalsIgnoreCase(buyerTaxNo)) {
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName, buyerTaxNo, itemName,
                    taxRate, totalAmount, "INVALID_RULE", "购买方税号与本园区税号(" + expectedTaxNo + ")不一致");
        }
        Optional<Map<String, String>> activeOpt = acquireActiveCredentials(tenantId, "VAT_INVOICE", false);
        if (activeOpt.isEmpty() || !StringUtils.hasText(activeOpt.get().get("endpoint"))) {
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName, buyerTaxNo, itemName,
                    taxRate, totalAmount, "STANDBY_MANUAL", "本地品目与税号规则预检通过；查验接口未开通，请财务人工核对电子票");
        }
        Map<String, String> c = activeOpt.get();
        try {
            String url = c.get("endpoint") + (c.get("endpoint").contains("?") ? "&" : "?")
                    + "fphm=" + URLEncoder.encode(invoiceNo, StandardCharsets.UTF_8)
                    + "&kprq=" + URLEncoder.encode(invoiceDate, StandardCharsets.UTF_8)
                    + "&je=" + totalAmount.toPlainString();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6))
                    .header("Authorization", "APPCODE " + c.get("appCode")).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName, buyerTaxNo, itemName,
                        taxRate, totalAmount, "STANDBY_MANUAL", "查验接口返回 HTTP " + resp.statusCode() + "，转人工复核");
            }
            JsonNode root = json.readTree(resp.body());
            boolean cancelled = "1".equals(root.path("zfbz").asText()) || "Y".equalsIgnoreCase(root.path("isCancelled").asText());
            if (cancelled) {
                return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName, buyerTaxNo, itemName,
                        taxRate, totalAmount, "CANCELLED_RED", "查验结果：该发票已作废或红冲");
            }
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName, buyerTaxNo, itemName,
                    taxRate, totalAmount, "VERIFIED_VALID", "查验接口返回正常（字段解析为通用接法，首次使用请人工比对一次结果）");
        } catch (Exception e) {
            return recordInvoiceLog(tenantId, withdrawalId, invoiceCode, invoiceNo, invoiceDate, sellerName, buyerTaxNo, itemName,
                    taxRate, totalAmount, "STANDBY_MANUAL", "查验接口调用失败，转人工复核：" + e.getMessage());
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

    /** 隐私小号：接口尚未对接，如实返回，不消耗额度、不返回任何虚构号码。 */
    public Map<String, Object> bindPrivacyCall(Long customerId, String salesPhone) {
        return Map.of("status", "NOT_IMPLEMENTED", "customerId", customerId,
                "message", "隐私小号外呼尚未对接（已预留开关与密钥位），请先使用认领后解锁的电话联系");
    }

    // ====================== 内部辅助 ======================

    private Map<String, Object> recordInvoiceLog(Long tenantId, Long withdrawalId, String code, String no, String date,
                                                 String seller, String buyerTax, String item, BigDecimal rate,
                                                 BigDecimal amount, String status, String msg) {
        jdbc.update("""
                INSERT INTO crm_invoice_verify_log
                  (tenant_id, withdrawal_id, invoice_code, invoice_no, invoice_date, seller_name, buyer_tax_no, item_name, tax_rate_pct, total_amount, verify_status, verify_msg, verified_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, tenantId, withdrawalId, code, no, date, seller, buyerTax, item, rate, amount, status, msg, MktAuditService.currentOperator());
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
                return Map.of("status", "FAKE_EMPTY", "origin", "", "trace", "快递公司查无任何揽收轨迹（疑似空单号），请人工复核");
            }
            String latest = data.get(0).path("context").asText("已有物流流转记录");
            boolean isSigned = "1".equals(root.path("ischeck").asText()) || "3".equals(root.path("state").asText());
            return Map.of("status", isSigned ? "REAL_SIGNED" : "IN_TRANSIT", "origin", "", "trace", latest);
        } catch (Exception e) {
            return Map.of("status", "STANDBY_MANUAL", "origin", "", "trace", "轨迹查询失败转人工复核：" + e.getMessage());
        }
    }

    private boolean sendImCard(String webhookUrl, String title, String content) {
        if (!StringUtils.hasText(webhookUrl)) return false;
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
                     ORDER BY RAND() LIMIT ?
                    """, tenantId, warehouseId, warehouseId, ym, limit);
        } catch (Exception e) { return List.of(); }
    }

    private List<Map<String, Object>> queryDbConnectors(Long tenantId) {
        try {
            return jdbc.queryForList("SELECT * FROM crm_ext_connector_config WHERE tenant_id = ? ORDER BY sort_order, id", tenantId);
        } catch (Exception e) { return List.of(); }
    }

    private Map<String, Object> queryConnectorRow(Long tenantId, String code) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM crm_ext_connector_config WHERE tenant_id = ? AND connector_code = ? LIMIT 1", tenantId, code);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) { return null; }
    }

    private List<Map<String, Object>> mergeWithSeeds(List<Map<String, Object>> dbRows, String curMonth) {
        Map<String, Map<String, Object>> byCode = new HashMap<>();
        for (Map<String, Object> r : dbRows) byCode.put(String.valueOf(r.get("connector_code")), r);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DefaultConnectorSeed s : DEFAULT_SEEDS) {
            Map<String, Object> r = byCode.get(s.code());
            boolean enabled = r != null ? toInt(r.get("enabled")) == 1 : s.enabled() == 1;
            int quota = r != null ? toInt(r.get("monthly_quota_limit")) : s.quota();
            String rowMonth = r != null && r.get("usage_month") != null ? String.valueOf(r.get("usage_month")) : "";
            int used = r != null && curMonth.equals(rowMonth) ? toInt(r.get("month_used_count")) : 0;
            Map<String, String> plainCreds = r == null ? Map.of() : decodeCredentials(str(r.get("credentials_cipher")));
            Map<String, String> maskedCreds = new LinkedHashMap<>();
            for (String f : s.fields()) {
                String v = plainCreds.getOrDefault(f, "");
                maskedCreds.put(f, NON_SECRET_FIELDS.contains(f) ? v : maskVal(v));
            }
            boolean configured = coreSecretPresent(plainCreds);
            String health;
            if (!enabled) health = "STANDBY";
            else if (quota > 0 && used >= quota) health = "QUOTA_FUSED";
            else if (!configured) health = "MISSING_KEY";
            else if ("DEV".equals(s.readiness())) health = "DEV_PENDING";
            else health = r != null && StringUtils.hasText(str(r.get("last_health_status"))) ? str(r.get("last_health_status")) : "READY";

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("connectorCode", s.code());
            item.put("categoryCode", s.category());
            item.put("displayName", s.name());
            item.put("feeTier", s.feeTier());
            item.put("feeDesc", s.feeDesc());
            item.put("description", s.desc());
            item.put("readiness", s.readiness());
            item.put("enabled", enabled);
            item.put("gdLockOnly", r != null ? toInt(r.get("gd_lock_only")) == 1 : s.gdLock() == 1);
            item.put("monthlyQuotaLimit", quota);
            item.put("monthUsedCount", used);
            item.put("blockedOutGdCount", r == null ? 0 : toInt(r.get("blocked_out_gd_count")));
            item.put("fields", s.fields());
            item.put("credentials", maskedCreds);
            item.put("configured", configured);
            item.put("applyUrl", s.applyUrl());
            item.put("applyGuide", s.applyGuide());
            item.put("healthStatus", health);
            item.put("healthMessage", r == null ? null : str(r.get("last_health_msg")));
            out.add(item);
        }
        return out;
    }

    public Map<String, String> loadPlainCredentials(Long tenantId, String code) {
        try {
            List<String> list = jdbc.queryForList(
                    "SELECT credentials_cipher FROM crm_ext_connector_config WHERE tenant_id = ? AND connector_code = ? LIMIT 1",
                    String.class, tenantId, code);
            return list == null || list.isEmpty() ? Map.of() : decodeCredentials(list.get(0));
        } catch (Exception e) { return Map.of(); }
    }

    private static boolean coreSecretPresent(Map<String, String> creds) {
        return creds.entrySet().stream()
                .anyMatch(e -> !NON_SECRET_FIELDS.contains(e.getKey()) && !"securityJsCode".equals(e.getKey()) && StringUtils.hasText(e.getValue()));
    }

    private String encodeCredentials(Map<String, String> creds) {
        try {
            String raw = json.writeValueAsString(creds);
            if (fieldEncryption != null && fieldEncryption.isConfigured()) return fieldEncryption.encrypt(raw);
            if (coreSecretPresent(creds)) throw new BizException(400, "未配置字段加密密钥，禁止明文保存接口密钥");
            return raw;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, String> decodeCredentials(String rawOrCipher) {
        if (!StringUtils.hasText(rawOrCipher)) return Map.of();
        try {
            String plain = rawOrCipher.startsWith("v1:")
                    ? (fieldEncryption != null && fieldEncryption.isConfigured() ? fieldEncryption.decrypt(rawOrCipher) : "{}")
                    : rawOrCipher;
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

    private void updateHealth(Long tenantId, String code, String status, String msg) {
        execSafe("UPDATE crm_ext_connector_config SET last_health_status = ?, last_health_msg = ?, last_health_at = NOW() WHERE tenant_id = ? AND connector_code = ?",
                status, msg, tenantId, code);
    }

    static DefaultConnectorSeed findSeed(String code) {
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
    private static LocalDateTime toLdt(Object o) {
        return o instanceof LocalDateTime l ? l : java.sql.Timestamp.valueOf(String.valueOf(o)).toLocalDateTime();
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
