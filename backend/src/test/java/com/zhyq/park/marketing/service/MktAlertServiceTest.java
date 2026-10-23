package com.zhyq.park.marketing.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 运营告警：通道、去重、转义，以及推送失败不影响业务。 */
class MktAlertServiceTest {
    private record Sent(URI uri, String body) {}

    private static class Recording extends MktAlertService {
        final List<Sent> sent = new ArrayList<>();
        boolean fail;
        Recording(String bark, String webhook) { super(bark, webhook); }
        @Override void deliver(URI uri, String jsonBody) throws Exception {
            if (fail) throw new java.io.IOException("network down");
            sent.add(new Sent(uri, jsonBody));
        }
    }

    @Test void 没有配置通道时只记日志不推送() {
        var alerts = new Recording("", "");
        alerts.alert("k", "标题", "内容");
        alerts.notify("标题", "内容");
        assertThat(alerts.enabled()).isFalse();
        assertThat(alerts.sent).isEmpty();
    }

    @Test void Bark按标题和内容编码成路径() {
        var alerts = new Recording("https://api.day.app/KEY/", "");
        alerts.alert("k", "云山 拉取失败", "关联 #7 失败");
        assertThat(alerts.sent).hasSize(1);
        assertThat(alerts.sent.get(0).uri().toString()).startsWith("https://api.day.app/KEY/")
                .contains("%E4%BA%91%E5%B1%B1%20%E6%8B%89%E5%8F%96%E5%A4%B1%E8%B4%A5").endsWith("?group=zhyq");
        assertThat(alerts.sent.get(0).body()).isNull();
    }

    @Test void Webhook以JSON发送并转义特殊字符() {
        var alerts = new Recording("", "https://hooks.example.com/x");
        alerts.alert("k", "标题\"引号\"", "第一行\n第二行");
        var body = alerts.sent.get(0).body();
        assertThat(body).contains("\"title\":\"标题\\\"引号\\\"\"").contains("第一行\\n第二行").contains("\"time\":");
    }

    @Test void 同一个key在十二小时内只推送一次不同key各自推送() {
        var alerts = new Recording("https://api.day.app/KEY", "");
        alerts.alert("a", "t", "b");
        alerts.alert("a", "t", "b");
        alerts.alert("b", "t", "b");
        assertThat(alerts.sent).hasSize(2);
    }

    @Test void 普通通知不去重() {
        var alerts = new Recording("https://api.day.app/KEY", "");
        alerts.notify("完成", "1");
        alerts.notify("完成", "1");
        assertThat(alerts.sent).hasSize(2);
    }

    @Test void 推送失败不抛出异常() {
        var alerts = new Recording("https://api.day.app/KEY", "https://hooks.example.com/x");
        alerts.fail = true;
        assertThatCode(() -> alerts.alert("k", "t", "b")).doesNotThrowAnyException();
        assertThatCode(() -> alerts.notify("t", "b")).doesNotThrowAnyException();
    }

    @Test void 过长的内容被截断() {
        var alerts = new Recording("", "https://hooks.example.com/x");
        alerts.alert("k", "t", "x".repeat(1000));
        assertThat(alerts.sent.get(0).body()).contains("…").hasSizeLessThan(1000);
    }
}
