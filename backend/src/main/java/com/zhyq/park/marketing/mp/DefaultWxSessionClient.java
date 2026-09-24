package com.zhyq.park.marketing.mp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DefaultWxSessionClient implements WxSessionClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBase;
    private final Map<String, AccessToken> accessTokens = new ConcurrentHashMap<>();
    private record AccessToken(String value, long expiresAt) {}

    @Autowired
    public DefaultWxSessionClient(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), "https://api.weixin.qq.com");
    }

    /** Package-private endpoint injection keeps HTTP protocol tests local. */
    DefaultWxSessionClient(ObjectMapper objectMapper, HttpClient httpClient, String apiBase) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiBase = apiBase;
    }

    @Override public Session exchange(String appId, String appSecret, String jsCode) {
        String url = apiBase + "/sns/jscode2session?appid=" + enc(appId) + "&secret=" + enc(appSecret)
                + "&js_code=" + enc(jsCode) + "&grant_type=authorization_code";
        JsonNode n = request(HttpRequest.newBuilder(URI.create(url)).GET().build());
        checkError(n, "微信登录");
        if (!n.hasNonNull("openid") || !n.path("openid").isTextual()
                || !n.hasNonNull("session_key") || !n.path("session_key").isTextual())
            throw new BizException("微信登录失败: 返回缺少 openid/session_key");
        return new Session(n.get("openid").asText(), n.get("session_key").asText());
    }

    @Override public String exchangePhone(String appId, String appSecret, String phoneCode) {
        String token = accessToken(appId, appSecret);
        JsonNode n = post(apiBase + "/wxa/business/getuserphonenumber?access_token=" + enc(token), Map.of("code", phoneCode));
        int error = n.path("errcode").asInt();
        if (error == 40001 || error == 40014 || error == 42001) {
            accessTokens.remove(appId);
            // Stable token's normal mode returns the current token without invalidating other processes.
            token = accessToken(appId, appSecret);
            n = post(apiBase + "/wxa/business/getuserphonenumber?access_token=" + enc(token), Map.of("code", phoneCode));
        }
        checkError(n, "手机号授权");
        JsonNode info = n.path("phone_info");
        if (!appId.equals(info.path("watermark").path("appid").asText()))
            throw new BizException("手机号授权来源不匹配,请重新登录");
        String countryCode = info.path("countryCode").asText();
        String phone = info.path("purePhoneNumber").asText();
        if (!"86".equals(countryCode) || !phone.matches("^1\\d{10}$"))
            throw new BizException("暂仅支持中国大陆手机号");
        return phone;
    }

    /** Official getUnlimitedQRCode API. Never expose the access token to the client. */
    public byte[] invitationCode(String appId, String appSecret, String inviteCode, String env) {
        if (!inviteCode.matches("[A-Za-z0-9]{8}") || !java.util.Set.of("release", "trial", "develop").contains(env))
            throw new BizException("邀请码或版本无效");
        try {
            String token = accessToken(appId, appSecret);
            var body = Map.of("scene", inviteCode, "page", "pages/login/index", "env_version", env,
                    "check_path", "release".equals(env), "width", 430);
            var req = HttpRequest.newBuilder(URI.create(apiBase + "/wxa/getwxacodeunlimit?access_token=" + enc(token)))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            var response = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] data = response.body();
            boolean image = data.length > 8 && ((data[0] == (byte)137 && data[1] == 80 && data[2] == 78 && data[3] == 71)
                    || (data[0] == (byte)255 && data[1] == (byte)216));
            if (response.statusCode() != 200 || !image) {
                if (data.length > 0 && data[0] == '{') checkError(objectMapper.readTree(data), "邀请码生成");
                throw new BizException("暂无法生成小程序码，可先分享邀请卡片或复制邀请码");
            }
            return data;
        } catch (BizException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BizException("生成中断，请重试"); }
        catch (Exception e) { throw new BizException("生成小程序码失败，可先分享邀请卡片或复制邀请码"); }
    }

    private synchronized String accessToken(String appId, String appSecret) {
        AccessToken cached = accessTokens.get(appId);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.value();
        JsonNode n = post(apiBase + "/cgi-bin/stable_token", Map.of("grant_type", "client_credential",
                "appid", appId, "secret", appSecret, "force_refresh", false));
        checkError(n, "微信服务凭证获取");
        String value = n.path("access_token").asText();
        long expiresIn = n.path("expires_in").asLong();
        if (!StringUtils.hasText(value) || expiresIn <= 0) throw new BizException("微信服务凭证获取失败,请稍后重试");
        accessTokens.put(appId, new AccessToken(value, System.currentTimeMillis() + Math.max(1, expiresIn - 60) * 1000));
        return value;
    }

    private JsonNode post(String url, Map<String, ?> body) {
        try {
            return request(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build());
        } catch (BizException e) { throw e; }
        catch (Exception e) { throw new BizException("微信服务请求失败,请稍后重试"); }
    }

    private JsonNode request(HttpRequest original) {
        try {
            HttpRequest request = HttpRequest.newBuilder(original, (name, value) -> true).timeout(Duration.ofSeconds(8)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new BizException("微信服务暂不可用,请稍后重试");
            JsonNode body = objectMapper.readTree(response.body());
            if (body == null || !body.isObject()) throw new BizException("微信服务返回异常,请稍后重试");
            return body;
        } catch (BizException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("微信服务请求中断,请重试");
        } catch (Exception e) {
            // HTTP exceptions may contain URLs with AppSecret/access_token; never log the exception or URI.
            log.warn("微信服务请求失败 ({})", e.getClass().getSimpleName());
            throw new BizException("微信服务连接失败,请稍后重试");
        }
    }

    private static void checkError(JsonNode n, String action) {
        int code = n.path("errcode").asInt();
        if (code == 0) return;
        String message = switch (code) {
            case 40029, 40163 -> "微信授权凭证已失效或不属于当前小程序,请重新点击登录;若持续失败请检查 AppID 配置";
            case 40013, 40125, 40001 -> "微信 AppID/Secret 配置异常,请联系管理员";
            case 45011, 45009 -> "微信请求过于频繁,请稍后重试";
            case 40226 -> "微信暂不允许此账号登录,请使用账号密码登录";
            case 48001 -> "小程序暂无手机号授权权限,请使用账号密码登录";
            case -1 -> "微信服务繁忙,请稍后重试";
            default -> action + "失败（微信错误码 " + code + "）,请重试或联系管理员";
        };
        throw new BizException(message);
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
