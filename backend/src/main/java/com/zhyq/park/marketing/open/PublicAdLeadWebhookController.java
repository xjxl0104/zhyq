package com.zhyq.park.marketing.open;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.password.ClientAddressResolver;
import com.zhyq.park.marketing.password.PasswordAttemptLimiter;
import com.zhyq.park.marketing.service.MktConnectorHubService;
import com.zhyq.park.marketing.service.MktPublicSeaWashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * 巨量引擎「飞鱼 CRM」与腾讯线索通免费广告 Webhook 实时接收口：
 * 0 元接口费，收到广东本地推表单后 0 秒验签、幂等防重并过三道防撞闸写入内部公海热单池。
 */
@Slf4j
@Tag(name = "公开获客-巨量飞鱼与腾讯广告免费Webhook")
@RestController
@RequestMapping("/open/v1/ad-leads")
@RequiredArgsConstructor
public class PublicAdLeadWebhookController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final BizSettings bizSettings;
    private final MktPublicSeaWashService washService;
    private final MktConnectorHubService connectorHub;
    private final PasswordAttemptLimiter limiter;
    private final ClientAddressResolver addresses;

    @Operation(summary = "接收飞鱼/腾讯线索通/小红书聚光/百度基木鱼表单实时推送入公海")
    @PostMapping("/{channel}")
    public Result<Map<String, Object>> receive(
            @PathVariable String channel,
            @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Signature", required = false) String signature,
            @RequestHeader(value = "Timestamp", required = false) String timestamp,
            @RequestBody String rawBody,
            HttpServletRequest request) {
        limiter.check("ad-webhook:" + addresses.resolve(request), 300);
        String ch = channel == null ? "FEIYU" : channel.trim().toUpperCase(Locale.ROOT);

        // 读取对应渠道配置的验签密钥（兼容巨量飞鱼 AD_FEIYU / 腾讯 AD_TENCENT / 小红书 AD_XHS / 百度 AD_BAIDU）
        String secret = resolveChannelSecret(ch);
        if (StringUtils.hasText(secret)) {
            verifySignature(secret.trim(), timestamp, rawBody, signature);
        }

        JsonNode root;
        try {
            root = json.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
        } catch (Exception e) {
            throw new BizException(400, "Webhook JSON 格式错误");
        }

        // 支持飞鱼控制台“测试连通性”空报文或 test 标志秒回成功
        if (root.isEmpty() || Boolean.TRUE.equals(root.path("is_test").booleanValue())) {
            return Result.ok(Map.of("accepted", true, "mode", "ping_test"));
        }

        String leadId = firstText(root, "lead_id", "clue_id", "leads_id", "id");
        String phone = firstText(root, "telphone", "telephone", "phone", "mobile");
        String name = firstText(root, "name", "shop_name", "company_name", "adv_name");
        if (!StringUtils.hasText(leadId)) {
            leadId = DigestUtil.sha256Hex(rawBody).substring(0, 32);
        }

        String digest = DigestUtil.sha256Hex(rawBody);
        try {
            jdbc.update("""
                    INSERT INTO crm_ext_ad_lead_inbox (tenant_id, channel, external_lead_id, wash_result, payload_digest)
                    VALUES (1, ?, ?, 'PROCESSING', ?)
                    """, ch, leadId, digest);
        } catch (DuplicateKeyException dup) {
            return Result.ok(Map.of("accepted", true, "duplicate", true, "leadId", leadId));
        }

        if (!StringUtils.hasText(phone)) {
            jdbc.update("UPDATE crm_ext_ad_lead_inbox SET wash_result = 'SKIPPED_NO_PHONE' WHERE channel = ? AND external_lead_id = ?", ch, leadId);
            return Result.ok(Map.of("accepted", true, "inserted", false, "reason", "missing_phone"));
        }

        JsonNode remarkDict = root.path("remark_dict");
        String category = firstText(remarkDict, "主营类目", "发货类目", "品类", "category");
        Integer estDaily = extractInt(firstText(remarkDict, "日均单量", "预估单量", "daily_orders"));
        String location = firstText(root, "location", "city_name", "province_name");
        String advName = firstText(root, "adv_name", "site_name", "ad_name");
        String note = ch + "广告实时留资" + (StringUtils.hasText(advName) ? "（计划：" + advName + "）" : "");

        String targetName = StringUtils.hasText(name) ? name : (ch + "投流准货主");
        Map<String, Object> wash = washService.ingestAdWebhookLead(
                projectId, ch, targetName, phone, category, estDaily, location, note);
        boolean inserted = ((Number) wash.get("insertedRows")).intValue() > 0;
        Long cid = wash.get("lastCustomerId") instanceof Number n ? n.longValue() : null;
        jdbc.update("UPDATE crm_ext_ad_lead_inbox SET customer_id = ?, wash_result = ? WHERE channel = ? AND external_lead_id = ?",
                cid, inserted ? "INSERTED" : "BLOCKED_PROTECTION", ch, leadId);
        if (inserted && connectorHub != null) {
            connectorHub.broadcastHotLeadIfEnabled(ch + "广告实时推送", targetName, category, estDaily, location);
        }

        log.info("[ad-webhook] 接收 {} 线索 leadId={} inserted={}", ch, leadId, inserted);
        return Result.ok(Map.of("accepted", true, "inserted", inserted, "leadId", leadId));
    }

    private String resolveChannelSecret(String ch) {
        if ("FEIYU".equals(ch)) {
            String legacy = bizSettings.raw("marketing", "ext_feiyu_token");
            if (StringUtils.hasText(legacy)) return legacy;
        }
        if (connectorHub == null) return null;
        return switch (ch) {
            case "FEIYU" -> connectorHub.loadPlainCredentials(1L, "AD_FEIYU").get("signatureSecret");
            case "TENCENT" -> connectorHub.loadPlainCredentials(1L, "AD_TENCENT").get("tokenSecret");
            case "XHS" -> connectorHub.loadPlainCredentials(1L, "AD_XHS").get("secret");
            case "BAIDU" -> connectorHub.loadPlainCredentials(1L, "AD_BAIDU").get("token");
            default -> null;
        };
    }

    private static void verifySignature(String secret, String timestamp, String body, String signature) {
        if (!StringUtils.hasText(signature)) {
            throw new BizException(401, "缺少飞鱼 Webhook Signature 签名头");
        }
        String payload = (timestamp == null ? "" : timestamp) + (body == null ? "" : body);
        HMac hmac = new HMac(HmacAlgorithm.HmacSHA256, secret.getBytes(StandardCharsets.UTF_8));
        String expectedHex = hmac.digestHex(payload, StandardCharsets.UTF_8);
        String expectedBase64 = hmac.digestBase64(payload, StandardCharsets.UTF_8, false);
        if (!expectedHex.equalsIgnoreCase(signature.trim()) && !expectedBase64.equals(signature.trim())) {
            throw new BizException(401, "飞鱼 Webhook 签名校验不通过");
        }
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode()) return null;
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText().trim();
        }
        return null;
    }

    private static Integer extractInt(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String clean = raw.replaceAll("[^0-9]", "");
        return clean.isEmpty() ? null : Integer.parseInt(clean);
    }
}
