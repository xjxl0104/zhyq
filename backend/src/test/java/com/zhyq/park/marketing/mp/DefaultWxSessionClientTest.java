package com.zhyq.park.marketing.mp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class DefaultWxSessionClientTest {
    HttpServer server;
    DefaultWxSessionClient client;
    ObjectMapper mapper = new ObjectMapper();
    AtomicInteger tokenCalls = new AtomicInteger();
    AtomicInteger phoneCalls = new AtomicInteger();
    AtomicReference<String> requestBody = new AtomicReference<>();
    String phoneResponse = "{\"errcode\":0,\"phone_info\":{\"purePhoneNumber\":\"13800138000\",\"countryCode\":\"86\",\"watermark\":{\"appid\":\"app\"}}}";

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cgi-bin/stable_token", exchange -> {
            tokenCalls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            var payload = mapper.readTree(exchange.getRequestBody());
            assertThat(payload.path("appid").asText()).isEqualTo("app");
            assertThat(payload.path("force_refresh").asBoolean()).isFalse();
            byte[] bytes = "{\"access_token\":\"server-token\",\"expires_in\":7200}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/wxa/business/getuserphonenumber", exchange -> {
            phoneCalls.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = phoneResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/sns/jscode2session", exchange -> {
            byte[] bytes = "{\"errcode\":40029,\"errmsg\":\"invalid code, rid: internal\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        client = new DefaultWxSessionClient(mapper, HttpClient.newHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void exchangesDynamicPhoneCodeAndCachesStableToken() throws Exception {
        assertThat(client.exchangePhone("app", "secret", "phone-code")).isEqualTo("13800138000");
        assertThat(mapper.readTree(requestBody.get()).path("code").asText()).isEqualTo("phone-code");
        client.exchangePhone("app", "secret", "second-code");
        assertThat(tokenCalls.get()).isEqualTo(1);
        assertThat(phoneCalls.get()).isEqualTo(2);
    }
    @Test void rejectsMismatchedPhoneWatermark() {
        phoneResponse = phoneResponse.replace("\"appid\":\"app\"", "\"appid\":\"other-app\"");
        assertThatThrownBy(() -> client.exchangePhone("app", "secret", "code")).isInstanceOf(BizException.class)
                .hasMessageContaining("来源不匹配");
    }
    @Test void loginCodeErrorExplainsConfigurationWithoutReturningWechatDiagnostics() {
        assertThatThrownBy(() -> client.exchange("app", "secret", "bad"))
                .isInstanceOf(BizException.class).hasMessageContaining("AppID").hasMessageNotContaining("rid:");
    }
    @Test void phonePermissionErrorOffersPasswordLogin() {
        phoneResponse = "{\"errcode\":48001}";
        assertThatThrownBy(() -> client.exchangePhone("app", "secret", "code"))
                .isInstanceOf(BizException.class).hasMessageContaining("账号密码");
    }
}
