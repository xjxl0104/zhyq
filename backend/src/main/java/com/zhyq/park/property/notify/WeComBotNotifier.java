package com.zhyq.park.property.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.setting.BizSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 企业微信群机器人推送(工单用)。
 *
 * <p>Webhook 地址优先取 biz_setting(module=property, skey=wecom_workorder_webhook),
 * 由管理员在「物业报修 → 企业微信通知」里填;未配置时回退到启动参数
 * {@code zhyq.wecom.workorder-webhook}(环境变量 ZHYQ_WECOM_WORKORDER_WEBHOOK),
 * 两者都为空就静默跳过 —— 通知是旁路,不能影响建单。</p>
 *
 * <p>发送走 sendAsync 且只记日志:群机器人挂了、网络抖了都不该让报修失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComBotNotifier {

    public static final String MODULE = "property";
    public static final String KEY = "wecom_workorder_webhook";
    /** 企业微信群机器人地址前缀,防止把内部消息发到任意外部地址 */
    private static final String WEBHOOK_PREFIX = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send";

    private final BizSettings bizSettings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Value("${zhyq.wecom.workorder-webhook:}")
    private String fallbackWebhook;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    /** 当前配置的 webhook,未配置返回 null */
    public String webhook() {
        String url = bizSettings.raw(MODULE, KEY);
        if (url == null || url.isBlank()) {
            url = fallbackWebhook;
        }
        return url == null || url.isBlank() ? null : url.trim();
    }

    public boolean enabled() {
        return webhook() != null;
    }

    /** 保存 webhook(传空即关闭推送) */
    public void saveWebhook(String url) {
        String value = url == null ? "" : url.trim();
        if (!value.isEmpty() && !value.startsWith(WEBHOOK_PREFIX)) {
            throw new com.zhyq.park.common.exception.BizException(
                    "请填企业微信群机器人的 Webhook 地址(以 " + WEBHOOK_PREFIX + " 开头)");
        }
        int updated = jdbc.update(
                "UPDATE biz_setting SET svalue = ?, update_time = NOW() WHERE module = ? AND skey = ? AND deleted = 0",
                value, MODULE, KEY);
        if (updated == 0) {
            jdbc.update("INSERT INTO biz_setting (module, skey, svalue, remark, tenant_id, create_by, create_time, version, deleted) "
                            + "VALUES (?, ?, ?, ?, 1, 'system', NOW(), 1, 0)",
                    MODULE, KEY, value, "工单企业微信群机器人 Webhook");
        }
    }

    /** 发送 markdown 消息;未配置或失败只记日志,不抛异常 */
    public void sendMarkdown(String markdown) {
        String url = webhook();
        if (url == null) {
            log.debug("[wecom] 未配置工单群机器人 webhook,跳过推送");
            return;
        }
        post(url, markdown, false);
    }

    /** 测试发送:失败要让配置的人看见原因,故抛业务异常 */
    public void sendTest(String markdown) {
        String url = webhook();
        if (url == null) {
            throw new com.zhyq.park.common.exception.BizException("还没配置 Webhook 地址");
        }
        post(url, markdown, true);
    }

    private void post(String url, String markdown, boolean sync) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("content", markdown)));
        } catch (Exception e) {
            log.warn("[wecom] 消息序列化失败", e);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        if (!sync) {
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenAccept(r -> logResult(r.statusCode(), r.body()))
                    .exceptionally(e -> {
                        log.warn("[wecom] 推送失败", e);
                        return null;
                    });
            return;
        }
        try {
            HttpResponse<String> r = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            logResult(r.statusCode(), r.body());
            if (r.statusCode() != 200 || (r.body() != null && !r.body().contains("\"errcode\":0"))) {
                throw new com.zhyq.park.common.exception.BizException("企业微信返回:" + r.body());
            }
        } catch (com.zhyq.park.common.exception.BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[wecom] 测试推送失败", e);
            throw new com.zhyq.park.common.exception.BizException("推送失败:" + e.getMessage());
        }
    }

    private void logResult(int status, String body) {
        if (status == 200 && body != null && body.contains("\"errcode\":0")) {
            log.info("[wecom] 推送成功");
        } else {
            log.warn("[wecom] 推送异常 status={} body={}", status, body);
        }
    }
}
