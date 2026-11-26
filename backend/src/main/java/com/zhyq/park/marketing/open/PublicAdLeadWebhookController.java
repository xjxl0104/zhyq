package com.zhyq.park.marketing.open;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
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
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 广告线索回调接收口（飞鱼 / 腾讯线索通 / 小红书聚光 / 百度基木鱼）：
 * 渠道必须在连接器中心开启并填写验签密钥，否则一律拒收，防止被外部灌入假线索。
 * 注：各平台验签算法需按其官方文档核对一次（当前为 HMAC-SHA256(timestamp+body) 通用实现）。
 */
@Slf4j
@Tag(name = "公开获客-广告线索回调")
@RestController
@RequestMapping("/open/v1/ad-leads")
@RequiredArgsConstructor
public class PublicAdLeadWebhookController {

    private static final Set<String> CHANNELS = Set.of("FEIYU", "TENCENT", "XHS", "BAIDU");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MktPublicSeaWashService washService;
    private final MktConnectorHubService connectorHub;
    private final PasswordAttemptLimiter limiter;
    private final ClientAddressResolver addresses;

    @Operation(summary = "接收广告平台表单推送入公海（须已开启渠道并配置验签密钥）")
    @PostMapping("/{channel}")
    public Result<Map<String, Object>> receive(
            @PathVariable String channel,
            @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Signature", required = false) String signature,
            @RequestHeader(value = "Timestamp", required = false) String timestamp,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest request) {
        limiter.check("ad-webhook:" + addresses.resolve(request), 300);
        String ch = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(ch)) throw new BizException(404, "未知的广告渠道");

        String secret = resolveChannelSecret(ch);
        if (!StringUtils.hasText(secret)) {
            throw new BizException(403, "该广告渠道未在连接器中心开启或未配置验签密钥，已拒收");
        }
        verifySignature(secret.trim(), timestamp, rawBody, signature);

        String body = rawBody == null ? "" : rawBody;
        JsonNode root;
        try {
            root = json.readTree(body.isBlank() ? "{}" : body);
        } catch (Exception e) {
            throw new BizException(400, "Webhook JSON 格式错误");
        }

        // 平台控制台"测试连通性"报文（已通过验签）直接回成功
        if (root.isEmpty() || Boolean.TRUE.equals(root.path("is_test").booleanValue())) {
            return Result.ok(Map.of("accepted", true, "mode", "ping_test"));
        }

        String leadId = firstText(root, "lead_id", "clue_id", "leads_id", "id");
        String phone = firstText(root, "telphone", "telephone", "phone", "mobile");
        String name = firstText(root, "name", "shop_name", "company_name", "adv_name");
        String digest = DigestUtil.sha256Hex(body);
        if (!StringUtils.hasText(leadId)) leadId = digest.substring(0, 32);

        try {
            jdbc.update("""
                    INSERT INTO crm_ext_ad_lead_inbox (tenant_id, channel, external_lead_id, wash_result, payload_digest)
                    VALUES (?, ?, ?, 'PROCESSING', ?)
                    """, MyMetaObjectHandler.DEFAULT_TENANT_ID, ch, leadId, digest);
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
        Map<String, Object> wash;
        try {
            wash = washService.ingestAdWebhookLead(
                    projectId, ch, targetName, phone, category, estDaily, location, note);
        } catch (RuntimeException ex) {
            // 洗数入库异常时清理 PROCESSING 占位行，允许广告平台自动重试重推，避免付费投流线索永久丢失
            jdbc.update("DELETE FROM crm_ext_ad_lead_inbox WHERE channel = ? AND external_lead_id = ? AND wash_result = 'PROCESSING'",
                    ch, leadId);
            throw ex;
        }
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
        if (connectorHub == null) return null;
        Map<String, String> creds = connectorHub.activeCredentials(MyMetaObjectHandler.DEFAULT_TENANT_ID, "AD_" + ch);
        return switch (ch) {
            case "FEIYU" -> creds.get("signatureSecret");
            case "TENCENT" -> creds.get("tokenSecret");
            case "XHS" -> creds.get("secret");
            case "BAIDU" -> creds.get("token");
            default -> null;
        };
    }

    static void verifySignature(String secret, String timestamp, String body, String signature) {
        if (!StringUtils.hasText(signature)) {
            throw new BizException(401, "缺少 Signature 签名头");
        }
        if (StringUtils.hasText(timestamp) && timestamp.trim().matches("\\d{10,13}")) {
            long ts = Long.parseLong(timestamp.trim());
            long tsMillis = timestamp.trim().length() == 13 ? ts : ts * 1000;
            if (Math.abs(System.currentTimeMillis() - tsMillis) > 10 * 60 * 1000L) {
                throw new BizException(401, "回调时间戳已过期，疑似重放请求");
            }
        }
        String payload = (timestamp == null ? "" : timestamp) + (body == null ? "" : body);
        HMac hmac = new HMac(HmacAlgorithm.HmacSHA256, secret.getBytes(StandardCharsets.UTF_8));
        String expectedHex = hmac.digestHex(payload, StandardCharsets.UTF_8);
        String expectedBase64 = hmac.digestBase64(payload, StandardCharsets.UTF_8, false);
        byte[] given = signature.trim().getBytes(StandardCharsets.UTF_8);
        boolean ok = MessageDigest.isEqual(expectedHex.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))
                || MessageDigest.isEqual(expectedBase64.getBytes(StandardCharsets.UTF_8), given);
        if (!ok) throw new BizException(401, "回调签名校验不通过");
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
