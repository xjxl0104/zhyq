package com.zhyq.park.marketing.yunshan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

/** 每条客户仓库映射保存自己的连接，历史映射可沿用已有共享连接。 */
@Service
public class YunshanConnections {
    private final YunshanClient shared;
    private final ObjectMapper json;
    private final FieldEncryptionService encryption;
    private final String allowedOrigins;

    public YunshanConnections(YunshanClient shared, ObjectMapper json, FieldEncryptionService encryption,
            @Value("${zhyq.yunshan.allowed-origins:}") String allowedOrigins) {
        this.shared = shared; this.json = json; this.encryption = encryption; this.allowedOrigins = allowedOrigins;
    }

    public record Input(@NotBlank @Pattern(regexp = "SHARED|CUSTOM") String mode,
                        @Size(max = 500) String baseUrl, @Size(max = 256) String appId,
                        @Size(max = 8192) String publicKey) {}

    public YunshanClient preview(Input input) {
        if (input == null || "SHARED".equals(input.mode())) {
            if (!shared.configured()) throw new BizException("共享接口尚未配置，请填写客户专用接口");
            return shared;
        }
        if (!"CUSTOM".equals(input.mode()) || input.appId() == null || input.appId().isBlank()
                || input.appId().length() > 256 || input.publicKey() == null || input.publicKey().length() > 8192)
            throw new BizException("请填写接口地址、App ID 和 RSA 公钥");
        String url = validateBaseUrl(input.baseUrl());
        if (input.appId().chars().anyMatch(c -> c < 32 || c > 126)) throw new BizException("App ID 格式不正确");
        try { YunshanSigner.parsePublicKey(input.publicKey()); }
        catch (IllegalStateException e) { throw new BizException("RSA 公钥格式无效，请核对接口提供方的公钥"); }
        return new YunshanClient(json, url, input.appId().trim(), input.publicKey().trim());
    }

    public void store(MktYunshanVolumeMap mapping, Input input) {
        preview(input);
        if (input == null || "SHARED".equals(input.mode())) return;
        mapping.setConnectionBaseUrl(validateBaseUrl(input.baseUrl()));
        mapping.setConnectionAppIdEnc(encryption.encrypt(input.appId().trim()));
        mapping.setConnectionPublicKeyEnc(encryption.encrypt(input.publicKey().trim()));
    }

    public YunshanClient forMapping(MktYunshanVolumeMap mapping) {
        if (mapping.getConnectionBaseUrl() == null) return shared;
        return preview(new Input("CUSTOM", mapping.getConnectionBaseUrl(),
            encryption.decrypt(mapping.getConnectionAppIdEnc()), encryption.decrypt(mapping.getConnectionPublicKeyEnc())));
    }

    // 凭证仅发往管理员认可的接口服务，禁止把页面输入变成任意内网请求。
    String validateBaseUrl(String value) {
        try {
            if (value == null || value.length() > 500) throw new IllegalArgumentException();
            URI uri = URI.create(value.trim());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) throw new IllegalArgumentException();
            String origin = origin(uri);
            boolean approved = !shared.baseUrl().isBlank() && origin.equals(origin(URI.create(shared.baseUrl())));
            approved |= Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .anyMatch(s -> origin.equals(origin(URI.create(s))));
            if (!approved) throw new BizException("该接口服务尚未授权，请由管理员添加可信接口地址后重试");
            return uri.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException e) { throw new BizException("请填写有效的接口基础地址，不要包含查询参数或账号密码"); }
    }
    private static String origin(URI uri) {
        return uri.getScheme() + "://" + Objects.toString(uri.getHost(), "").toLowerCase(java.util.Locale.ROOT)
            + ":" + (uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort());
    }
}
