package com.zhyq.park.common.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Log-only adapters for optional external channels. */
public final class MockNotifyChannels {
    private MockNotifyChannels() {}

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
