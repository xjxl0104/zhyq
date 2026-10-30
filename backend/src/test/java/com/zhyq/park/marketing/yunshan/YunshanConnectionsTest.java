package com.zhyq.park.marketing.yunshan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.junit.jupiter.api.Test;
import java.security.KeyPairGenerator;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class YunshanConnectionsTest {
    final ObjectMapper json = new ObjectMapper();
    final FieldEncryptionService encryption = new FieldEncryptionService(Base64.getEncoder().encodeToString(new byte[32]));
    final YunshanClient shared = new YunshanClient(json, "https://approved.example/api", "shared", "key");
    final YunshanConnections connections = new YunshanConnections(shared, json, encryption, "https://second.example");

    @Test void credentialsAreEncryptedAndNeverReturnedByMappingSerialization() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA"); keys.initialize(2048);
        String publicKey = Base64.getEncoder().encodeToString(keys.generateKeyPair().getPublic().getEncoded());
        var input = new YunshanConnections.Input("CUSTOM", "https://approved.example/api/", "customer-app-123", publicKey);
        var mapping = new MktYunshanVolumeMap();
        connections.store(mapping, input);
        assertThat(mapping.getConnectionAppIdEnc()).startsWith("v1:").doesNotContain(input.appId());
        assertThat(encryption.decrypt(mapping.getConnectionPublicKeyEnc())).isEqualTo(publicKey);
        assertThat(json.writeValueAsString(mapping)).doesNotContain(input.appId(), publicKey, "connectionAppIdEnc", "connectionPublicKeyEnc");
        assertThat(connections.forMapping(mapping).maskedAppId()).isEqualTo("cus****123");
        assertThat(connections.forMapping(new MktYunshanVolumeMap())).isSameAs(shared);
    }

    @Test void warehouseInterfaceIsSharedByItsCustomersAndEncrypted() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA"); keys.initialize(2048);
        String publicKey = Base64.getEncoder().encodeToString(keys.generateKeyPair().getPublic().getEncoded());
        var mapper = mock(com.zhyq.park.marketing.mapper.MktWarehouseYunshanConnMapper.class);
        var withStore = new YunshanConnections(shared, json, encryption, "https://second.example", mapper);
        var warehouse = new com.zhyq.park.marketing.entity.MktWarehouse(); warehouse.setId(7L); warehouse.setTenantId(1L);
        when(mapper.selectOne(any())).thenReturn(null);
        assertThat(withStore.forWarehouse(7L)).isNull();
        assertThat(withStore.forMapping(mappingOf(7L))).isSameAs(shared);

        withStore.saveWarehouse(warehouse, new YunshanConnections.Input("CUSTOM", "https://second.example/api/", "wh-app-777", publicKey));
        var saved = org.mockito.ArgumentCaptor.forClass(com.zhyq.park.marketing.entity.MktWarehouseYunshanConn.class);
        verify(mapper).insert(saved.capture());
        var row = saved.getValue();
        assertThat(row.getBaseUrl()).isEqualTo("https://second.example/api");
        assertThat(row.getAppIdEnc()).startsWith("v1:").doesNotContain("wh-app-777");
        assertThat(json.writeValueAsString(row)).doesNotContain("wh-app-777", publicKey);

        when(mapper.selectOne(any())).thenReturn(row);
        assertThat(withStore.forWarehouse(7L).maskedAppId()).isEqualTo("wh-****777");
        assertThat(withStore.forMapping(mappingOf(7L)).maskedAppId()).isEqualTo("wh-****777");
        assertThat(withStore.resolve(7L, new YunshanConnections.Input("SHARED", null, null, null)).maskedAppId()).isEqualTo("wh-****777");
        // 客户专用连接仍优先于云仓接口
        var custom = mappingOf(7L); custom.setConnectionBaseUrl("https://approved.example/api");
        custom.setConnectionAppIdEnc(encryption.encrypt("customer-app-123")); custom.setConnectionPublicKeyEnc(encryption.encrypt(publicKey));
        assertThat(withStore.forMapping(custom).maskedAppId()).isEqualTo("cus****123");
    }
    private static MktYunshanVolumeMap mappingOf(Long warehouseId) { var m = new MktYunshanVolumeMap(); m.setWarehouseId(warehouseId); return m; }

    @Test void rejectsUntrustedOriginsAndCredentialBearingUrlsBeforeMakingRequests() {
        for (String url : new String[]{"http://127.0.0.1", "https://approved.example.evil.test", "https://approved.example:444",
                "https://user:password@approved.example", "https://approved.example?token=x", "file:///tmp/data"})
            assertThatThrownBy(() -> connections.validateBaseUrl(url)).isInstanceOf(com.zhyq.park.common.exception.BizException.class);
        assertThat(connections.validateBaseUrl("https://second.example/api")).isEqualTo("https://second.example/api");
    }

    @Test void missingSharedConfigurationDoesNotSilentlyEnableSync() {
        var empty = mock(YunshanClient.class);
        assertThatThrownBy(() -> new YunshanConnections(empty, json, encryption, "").preview(
            new YunshanConnections.Input("SHARED", null, null, null))).hasMessageContaining("尚未配置");
    }
}
