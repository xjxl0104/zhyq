package com.zhyq.park.common.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知渠道 Mock 实现(批次② 默认兜底)。
 *
 * <p>三个渠道均只记日志、一律受理成功,用于无真实通道时联调。真实通道(阿里短信/微信模板消息/
 * 落库站内信)后续各自替换为独立 @Component 即可,{@link NotificationService} 与调用方不变。
 * 三个实现放同一文件(package-private @Component),仅为减少样板,Spring 照常纳管。</p>
 */
public final class MockNotifyChannels {
    private MockNotifyChannels() {}

    @Slf4j
    @Component
    static class InAppChannel implements NotifyChannel {
        public String channel() { return "inapp"; }
        public boolean send(NotifyMessage m) {
            log.info("[notify:inapp] to={} {} / {} ({}:{})", m.toRefId(), m.title(), m.content(), m.bizType(), m.bizId());
            return true;
        }
    }

    @Slf4j
    @Component
    static class SmsChannel implements NotifyChannel {
        public String channel() { return "sms"; }
        public boolean send(NotifyMessage m) {
            log.info("[notify:sms] to={} {}", m.toRefId(), m.title());
            return true;
        }
    }

    @Slf4j
    @Component
    static class WechatChannel implements NotifyChannel {
        public String channel() { return "wechat"; }
        public boolean send(NotifyMessage m) {
            log.info("[notify:wechat] to={} {}", m.toRefId(), m.title());
            return true;
        }
    }
}
