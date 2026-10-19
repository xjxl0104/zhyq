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
