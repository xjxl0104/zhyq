package com.zhyq.park.marketing.mp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultWxSessionClient implements WxSessionClient {
    private final ObjectMapper objectMapper;
    @Override public Session exchange(String appId, String appSecret, String jsCode) {
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + enc(appId) + "&secret=" + enc(appSecret)
                    + "&js_code=" + enc(jsCode) + "&grant_type=authorization_code";
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                    .send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            JsonNode n = objectMapper.readTree(response.body());
            if (n.hasNonNull("errcode") && n.get("errcode").asInt() != 0)
                throw new BizException("微信登录失败: " + n.path("errmsg").asText());
            if (!n.hasNonNull("openid") || !n.path("openid").isTextual()
                    || !n.hasNonNull("session_key") || !n.path("session_key").isTextual())
                throw new BizException("微信登录失败: 返回缺少 openid/session_key");
            return new Session(n.get("openid").asText(), n.get("session_key").asText());
        } catch (BizException e) { throw e; }
        catch (Exception e) { log.error("[mp] code2Session 失败", e); throw new BizException("微信登录失败,请重试"); }
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
