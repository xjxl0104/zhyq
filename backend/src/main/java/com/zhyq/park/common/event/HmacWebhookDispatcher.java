package com.zhyq.park.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * {@link WebhookDispatcher} 默认实现(批次① 骨架)。
 *
 * <p>默认关闭:{@code zhyq.webhook.enabled=false} 时直接跳过,不做任何外部请求。
 * 开启且配置了 url/secret 时,当前也仅计算 HMAC-SHA256 签名并记录日志 —— 真实 HTTP 外发
 * 留待需要时补齐(替换本类 sign 之后的 TODO 段),调用方无需改动。</p>
 */
@Slf4j
@Component
public class HmacWebhookDispatcher implements WebhookDispatcher {

    @Value("${zhyq.webhook.enabled:false}")
    private boolean enabled;

    @Value("${zhyq.webhook.url:}")
    private String url;

    @Value("${zhyq.webhook.secret:}")
    private String secret;

    @Override
    public void dispatch(DomainEvent event) {
        if (!enabled || url == null || url.isBlank()) {
            return; // 默认关闭:静默跳过,绝不影响业务主流程
        }
        try {
            String payload = event.type() + "|" + event.occurredAt();
            String signature = sign(payload, secret);
            // TODO 接真实端点:POST url,带头 X-Zhyq-Signature=signature、X-Zhyq-Event=event.type()
            log.info("[webhook] 预留出口 type={} sig={} -> {}", event.type(), signature, url);
        } catch (Exception e) {
            log.warn("[webhook] 分发失败(已吞异常,不影响主流程) type={}", event.type(), e);
        }
    }

    /** HMAC-SHA256 签名,十六进制小写。 */
    private String sign(String payload, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec((key == null ? "" : key).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
