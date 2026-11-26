package com.zhyq.park.marketing.open;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.password.ClientAddressResolver;
import com.zhyq.park.marketing.password.PasswordAttemptLimiter;
import com.zhyq.park.marketing.service.MktConnectorHubService;
import com.zhyq.park.marketing.service.MktPublicSeaWashService;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 广告线索回调：未开启/未填密钥一律拒收，签名错误拒收，签名正确才受理。 */
class PublicAdLeadWebhookControllerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MktPublicSeaWashService wash = mock(MktPublicSeaWashService.class);
    private final MktConnectorHubService hub = mock(MktConnectorHubService.class);
    private final PublicAdLeadWebhookController controller = new PublicAdLeadWebhookController(
            jdbc, new ObjectMapper(), wash, hub, mock(PasswordAttemptLimiter.class), mock(ClientAddressResolver.class));
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void rejectsWhenChannelHasNoSecretConfigured() {
        when(hub.activeCredentials(anyLong(), eq("AD_FEIYU"))).thenReturn(Map.of());
        assertThatThrownBy(() -> controller.receive("feiyu", null, "x", null, "{\"phone\":\"13800000000\"}", request))
                .isInstanceOf(BizException.class).hasMessageContaining("已拒收");
        verifyNoInteractions(wash);
    }

    @Test
    void rejectsUnknownChannelAndBadSignature() {
        assertThatThrownBy(() -> controller.receive("weibo", null, "x", null, "{}", request))
                .isInstanceOf(BizException.class).hasMessageContaining("未知");
        when(hub.activeCredentials(anyLong(), eq("AD_FEIYU"))).thenReturn(Map.of("signatureSecret", "s3cret"));
        assertThatThrownBy(() -> controller.receive("feiyu", null, "bad-sign", null, "{\"phone\":\"13800000000\"}", request))
                .isInstanceOf(BizException.class).hasMessageContaining("签名");
        verifyNoInteractions(wash);
    }

    @Test
    void acceptsTestPingWithValidSignature() {
        when(hub.activeCredentials(anyLong(), eq("AD_FEIYU"))).thenReturn(Map.of("signatureSecret", "s3cret"));
        String body = "{\"is_test\":true}";
        String sign = new HMac(HmacAlgorithm.HmacSHA256, "s3cret".getBytes(StandardCharsets.UTF_8))
                .digestHex(body, StandardCharsets.UTF_8);
        assertThat(controller.receive("feiyu", null, sign, null, body, request).getData())
                .containsEntry("mode", "ping_test");
    }

    @Test
    void cleansProcessingInboxRowOnWashFailureSoPlatformRetryIsNotDropped() {
        when(hub.activeCredentials(anyLong(), eq("AD_FEIYU"))).thenReturn(Map.of("signatureSecret", "s3cret"));
        when(wash.ingestAdWebhookLead(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("数据库瞬断"));
        String body = "{\"lead_id\":\"L9988\",\"phone\":\"13811112222\",\"name\":\"广州某店\"}";
        String sign = new HMac(HmacAlgorithm.HmacSHA256, "s3cret".getBytes(StandardCharsets.UTF_8))
                .digestHex(body, StandardCharsets.UTF_8);
        assertThatThrownBy(() -> controller.receive("feiyu", null, sign, null, body, request))
                .isInstanceOf(RuntimeException.class);
        verify(jdbc).update(contains("DELETE FROM crm_ext_ad_lead_inbox"), eq("FEIYU"), eq("L9988"));
    }
}
