package com.zhyq.park.marketing.open;

import com.zhyq.park.marketing.entity.MktWarehouseErp;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ErpHeartbeatControllerTest {
    @Test void signedHeartbeatUsesEmptyBodyAndReportsConnectionSeparatelyFromLastOrder() {
        ErpSignatureService signatures = mock(ErpSignatureService.class);
        MktWarehouseErp credential = new MktWarehouseErp(); credential.setEnv(2); credential.setStatus(1);
        credential.setLastHeartbeatAt(LocalDateTime.now()); credential.setLastSyncAt(null);
        when(signatures.verify("wh-1", "timestamp", "nonce", "signature", ""))
                .thenReturn(new ErpSignatureService.Ok(credential));
        ResponseEntity<Map<String, Object>> response = new ErpHeartbeatController(signatures)
                .heartbeat("wh-1", "timestamp", "nonce", "signature");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("ok", true).containsEntry("erp_status", 2)
                .containsEntry("last_sync_at", null).containsEntry("last_heartbeat_at", credential.getLastHeartbeatAt());
        verify(signatures).verify("wh-1", "timestamp", "nonce", "signature", "");
    }

    @Test void unauthenticatedRequestCannotClaimAHeartbeat() {
        ErpSignatureService signatures = mock(ErpSignatureService.class);
        when(signatures.verify(null, null, null, null, ""))
                .thenReturn(new ErpSignatureService.Fail("SIGN_INVALID", "缺少签名头"));
        ResponseEntity<Map<String, Object>> response = new ErpHeartbeatController(signatures)
                .heartbeat(null, null, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("ok", false).containsEntry("code", "SIGN_INVALID")
                .doesNotContainKeys("erp_status", "last_heartbeat_at");
    }
}
