package com.zhyq.park.marketing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运营告警：定时任务出错（云山拉取失败、佣金订单生成失败、自动月结失败等）时推送到手机，同时一定写错误日志。
 * 通道由环境变量配置（都不配置时只写日志）：
 *   ZHYQ_ALERT_BARK_URL    Bark 推送地址，形如 https://api.day.app/你的key
 *   ZHYQ_ALERT_WEBHOOK_URL 通用 Webhook，POST JSON {title, body, text, time}
 * 同一个 key 的告警 12 小时内只推一次，避免每天重复刷屏；任何推送失败都只记日志，不影响业务。
 */
@Slf4j
@Service
public class MktAlertService {
    static final Duration DEDUPE = Duration.ofHours(12);
    private static final int MAX_BODY = 300;

    private final String barkUrl;
    private final String webhookUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Instant> sent = new ConcurrentHashMap<>();

    public MktAlertService(@Value("${zhyq.alert.bark-url:}") String barkUrl,
                           @Value("${zhyq.alert.webhook-url:}") String webhookUrl) {
        this.barkUrl = barkUrl == null ? "" : barkUrl.trim();
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
    }

    public boolean enabled() { return StringUtils.hasText(barkUrl) || StringUtils.hasText(webhookUrl); }

    /** 故障告警：同一个 key 12 小时内只推送一次。 */
    public void alert(String key, String title, String body) {
        log.error("[alert] {} - {}", title, body);
        if (!enabled()) return;
        Instant now = Instant.now();
        Instant last = sent.get(key);
        if (last != null && Duration.between(last, now).compareTo(DEDUPE) < 0) return;
        sent.put(key, now);
        push(title, body);
    }

    /** 普通通知（如自动月结完成）：不去重。 */
    public void notify(String title, String body) {
        log.info("[notify] {} - {}", title, body);
        if (enabled()) push(title, body);
    }

    private void push(String title, String body) {
        String text = body == null ? "" : (body.length() > MAX_BODY ? body.substring(0, MAX_BODY) + "…" : body);
        try {
            if (StringUtils.hasText(barkUrl)) deliver(URI.create(barkUrl.replaceAll("/+$", "") + "/" + enc(title) + "/" + enc(text) + "?group=zhyq"), null);
            if (StringUtils.hasText(webhookUrl)) {
                deliver(URI.create(webhookUrl), "{\"title\":" + json(title) + ",\"body\":" + json(text) + ",\"text\":" + json(title + "\n" + text)
                        + ",\"time\":" + json(Instant.now().toString()) + "}");
            }
        } catch (Exception failure) {
            log.warn("[alert] 推送失败：{}", failure.toString());
        }
    }

    /** 发送一次 HTTP 请求；body 为空发 GET，否则 POST JSON。测试里覆盖本方法。 */
    void deliver(URI uri, String jsonBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8));
        request = jsonBody == null ? request.GET()
                : request.header("Content-Type", "application/json; charset=utf-8").POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 400) log.warn("[alert] 推送通道返回 HTTP {}", response.statusCode());
    }

    static String enc(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20"); }

    static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : (value == null ? "" : value).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
