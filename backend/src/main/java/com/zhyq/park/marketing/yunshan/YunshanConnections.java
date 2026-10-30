package com.zhyq.park.marketing.yunshan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseYunshanConn;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.marketing.mapper.MktWarehouseYunshanConnMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

/** 连接优先级：客户专用 > 云仓接口 > 服务端共享接口。云仓接口配置一次，该云仓下所有客户共用。 */
@Service
public class YunshanConnections {
    private final YunshanClient shared;
    private final ObjectMapper json;
    private final FieldEncryptionService encryption;
    private final String allowedOrigins;
    private final MktWarehouseYunshanConnMapper warehouseConns;

    @Autowired
    public YunshanConnections(YunshanClient shared, ObjectMapper json, FieldEncryptionService encryption,
            @Value("${zhyq.yunshan.allowed-origins:}") String allowedOrigins, MktWarehouseYunshanConnMapper warehouseConns) {
        this.shared = shared; this.json = json; this.encryption = encryption; this.allowedOrigins = allowedOrigins;
        this.warehouseConns = warehouseConns;
    }

    public YunshanConnections(YunshanClient shared, ObjectMapper json, FieldEncryptionService encryption, String allowedOrigins) {
        this(shared, json, encryption, allowedOrigins, null);
    }

    /** 该云仓自己配置的接口；未配置返回 null。 */
    public YunshanClient forWarehouse(Long warehouseId) {
        MktWarehouseYunshanConn row = warehouseId == null || warehouseConns == null ? null : warehouseConns.selectOne(
            new LambdaQueryWrapper<MktWarehouseYunshanConn>().eq(MktWarehouseYunshanConn::getWarehouseId, warehouseId).last("LIMIT 1"));
        if (row == null) return null;
        return preview(new Input("CUSTOM", row.getBaseUrl(), encryption.decrypt(row.getAppIdEnc()), encryption.decrypt(row.getPublicKeyEnc())));
    }

    /** 每个云仓只用自己的接口：该云仓下的客户统一使用它，未配置时必须先配置，不退回其他接口。 */
    public YunshanClient requireWarehouse(Long warehouseId) {
        YunshanClient own = forWarehouse(warehouseId);
        if (own == null) throw new BizException("该云仓还没有配置发货量接口，请先在 ERP 对接页配置云仓发货量接口");
        return own;
    }

    /** 新增客户对应、读取授权目录时用：一律使用该云仓自己的接口。 */
    public YunshanClient resolve(Long warehouseId, Input input) {
        return requireWarehouse(warehouseId);
    }


    /** 保存云仓接口（先校验地址与公钥）。 */
    public YunshanClient saveWarehouse(MktWarehouse warehouse, Input input) {
        if (input == null || !"CUSTOM".equals(input.mode())) throw new BizException("请填写接口地址、App ID 和 RSA 公钥");
        YunshanClient client = preview(input);
        MktWarehouseYunshanConn row = warehouseConns.selectOne(
            new LambdaQueryWrapper<MktWarehouseYunshanConn>().eq(MktWarehouseYunshanConn::getWarehouseId, warehouse.getId()).last("LIMIT 1"));
        boolean create = row == null;
        if (create) {
            row = new MktWarehouseYunshanConn();
            row.setWarehouseId(warehouse.getId()); row.setTenantId(warehouse.getTenantId()); row.setProjectId(warehouse.getProjectId());
        }
        row.setBaseUrl(validateBaseUrl(input.baseUrl()));
        row.setAppIdEnc(encryption.encrypt(input.appId().trim()));
        row.setPublicKeyEnc(encryption.encrypt(input.publicKey().trim()));
        if (create) warehouseConns.insert(row); else warehouseConns.updateById(row);
        return client;
    }

    public record Input(@NotBlank @Pattern(regexp = "SHARED|CUSTOM") String mode,
                        @Size(max = 500) String baseUrl, @Size(max = 256) String appId,
                        @Size(max = 8192) String publicKey) {}

    public YunshanClient preview(Input input) {
        if (input == null || "SHARED".equals(input.mode())) throw new BizException("请填写接口地址、App ID 和 RSA 公钥");
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
        if (input == null || "SHARED".equals(input.mode())) return;
        preview(input);
        mapping.setConnectionBaseUrl(validateBaseUrl(input.baseUrl()));
        mapping.setConnectionAppIdEnc(encryption.encrypt(input.appId().trim()));
        mapping.setConnectionPublicKeyEnc(encryption.encrypt(input.publicKey().trim()));
    }

    public YunshanClient forMapping(MktYunshanVolumeMap mapping) {
        if (mapping.getConnectionBaseUrl() == null) return requireWarehouse(mapping.getWarehouseId());
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
