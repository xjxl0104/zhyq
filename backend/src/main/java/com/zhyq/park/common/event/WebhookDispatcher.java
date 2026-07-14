package com.zhyq.park.common.event;

/**
 * 领域事件对外 Webhook 出口(批次① 地基,预留骨架)。
 *
 * <p>用途:把领域事件以 HMAC 签名的 HTTP 回调推给第三方(或未来的 AI agent 网关)。
 * 本轮仅落地"接口 + 可切换开关 + HMAC 签名位",默认关闭({@code zhyq.webhook.enabled=false}),
 * 不发起真实外部请求。接真实端点时替换实现即可,不动调用方。</p>
 */
public interface WebhookDispatcher {

    /**
     * 分发一个领域事件到已配置的 Webhook 端点。
     * 实现须:序列化事件 → 用 secret 生成 HMAC-SHA256 签名 → 带 X-Zhyq-Signature 头 POST。
     * 关闭或未配置端点时应静默跳过(不得影响业务主流程)。
     */
    void dispatch(DomainEvent event);
}
