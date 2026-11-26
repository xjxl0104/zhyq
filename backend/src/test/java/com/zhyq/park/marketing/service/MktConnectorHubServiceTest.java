package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.auth.BusinessRouteAccess;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 连接器中心：权限隔离、原子额度熔断、省钱锁、密钥加密保存守卫、验店防伪造、隐私小号如实返回、专票规则预审。
 */
class MktConnectorHubServiceTest {

    private static final String KEY_B64 = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private static Map<String, Object> row(String code, int enabled, int gdLock, int quota, int used, String cipher) {
        Map<String, Object> r = new HashMap<>();
        r.put("connector_code", code);
        r.put("enabled", enabled);
        r.put("gd_lock_only", gdLock);
        r.put("monthly_quota_limit", quota);
        r.put("usage_month", YearMonth.now().toString());
        r.put("month_used_count", used);
        r.put("blocked_out_gd_count", 0);
        r.put("credentials_cipher", cipher);
        r.put("last_health_status", "READY");
        return r;
    }

    @Test
    void connectorRoutesBlockExternalMpRoleAndAllowInternalRoles() {
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/connectors/overview", "GET", Set.of("ROLE_MP"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/connectors/QCC_PRO", "PUT", Set.of("ROLE_MP"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/connectors/overview", "GET",
                Set.of("crm:marketing:setting:query"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/connectors/waybill-spot-check", "POST",
                Set.of("crm:marketing:settlement:edit"))).isTrue();
    }

    @Test
    void overviewShowsEighteenConnectorsWithReadinessAndMaskedSecrets() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FieldEncryptionService crypto = new FieldEncryptionService(KEY_B64);
        String cipher = crypto.encrypt("{\"appKey\":\"qcc_ak_123456\",\"secretKey\":\"qcc_sk_987654\"}");
        when(jdbc.queryForList(contains("FROM crm_ext_connector_config"), eq(1L)))
                .thenReturn(List.of(row("QCC_PRO", 1, 1, 2000, 2000, cipher)));

        MktConnectorHubService hub = new MktConnectorHubService(jdbc, new ObjectMapper(), crypto, mock(MktAuditService.class));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) hub.hubOverview().get("connectors");
        assertThat(list).hasSize(18);
        Map<String, Object> qcc = list.stream().filter(m -> "QCC_PRO".equals(m.get("connectorCode"))).findFirst().orElseThrow();
        assertThat(qcc.get("healthStatus")).isEqualTo("QUOTA_FUSED");
        assertThat(((Map<?, ?>) qcc.get("credentials")).get("appKey")).isEqualTo("qcc****456");
        Map<String, Object> axb = list.stream().filter(m -> "PRIVACY_AXB".equals(m.get("connectorCode"))).findFirst().orElseThrow();
        assertThat(axb.get("readiness")).isEqualTo("DEV");
        // 广告回调默认关闭（未填验签密钥不得接收）
        assertThat(list.stream().filter(m -> String.valueOf(m.get("connectorCode")).startsWith("AD_"))
                .allMatch(m -> Boolean.FALSE.equals(m.get("enabled")))).isTrue();
    }

    @Test
    void acquireUsesAtomicQuotaUpdateAndRespectsGuangdongLock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FieldEncryptionService crypto = new FieldEncryptionService(KEY_B64);
        String cipher = crypto.encrypt("{\"appKey\":\"qcc_ak_123456\",\"secretKey\":\"qcc_sk_987654\"}");
        when(jdbc.queryForList(contains("connector_code = ?"), eq(1L), eq("QCC_PRO")))
                .thenReturn(List.of(row("QCC_PRO", 1, 1, 2000, 10, cipher)));
        MktConnectorHubService hub = new MktConnectorHubService(jdbc, new ObjectMapper(), crypto, mock(MktAuditService.class));

        // 原子扣减成功 → 返回凭证
        when(jdbc.update(contains("month_used_count < monthly_quota_limit"), any(Object[].class))).thenReturn(1);
        assertThat(hub.acquireActiveCredentials(1L, "QCC_PRO", false)).isPresent();

        // 并发下额度已被别人用满（更新 0 行）→ 熔断，不返回凭证
        when(jdbc.update(contains("month_used_count < monthly_quota_limit"), any(Object[].class))).thenReturn(0);
        assertThat(hub.acquireActiveCredentials(1L, "QCC_PRO", false)).isEmpty();

        // 外省主体 + 省钱锁 → 不扣额度
        clearInvocations(jdbc);
        assertThat(hub.acquireActiveCredentials(1L, "QCC_PRO", true)).isEmpty();
        verify(jdbc, never()).update(contains("month_used_count < monthly_quota_limit"), any(Object[].class));
    }

    @Test
    void savingSecretsWithoutEncryptionKeyIsRejected() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MktConnectorHubService hub = new MktConnectorHubService(jdbc, new ObjectMapper(),
                new FieldEncryptionService(""), mock(MktAuditService.class));
        assertThatThrownBy(() -> hub.saveConnector("QCC_PRO", Map.of(
                "enabled", true, "credentials", Map.of("appKey", "qcc_live_123456", "secretKey", "sk_live_987654"))))
                .isInstanceOf(BizException.class).hasMessageContaining("ZHYQ_FIELD_ENCRYPTION_KEY");
        verify(jdbc, never()).update(contains("INSERT INTO crm_ext_connector_config"), any(Object[].class));
    }

    @Test
    void shopVerifySubmitIsSelfReportedOnlyAndRejectsExpiredOrRepeated() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MktConnectorHubService hub = new MktConnectorHubService(jdbc, new ObjectMapper(),
                new FieldEncryptionService(""), mock(MktAuditService.class));
        when(jdbc.update(contains("status = 'PENDING' AND expires_at > NOW()"), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> hub.submitShopVerifyResult("sv_x", 30000, null, null, "自报"))
                .isInstanceOf(BizException.class).hasMessageContaining("已过期");

        when(jdbc.update(contains("status = 'PENDING' AND expires_at > NOW()"), any(Object[].class))).thenReturn(1);
        assertThat(hub.submitShopVerifyResult("sv_y", 30000, null, null, "自报").get("status")).isEqualTo("SELF_REPORTED");

        Map<String, Object> invite = hub.createShopVerifyInvite(901L, "DOUYIN", "某抖店");
        assertThat(invite.get("oauthConfigured")).isEqualTo(false);
        assertThat(hub.bindPrivacyCall(901L, null).get("status")).isEqualTo("NOT_IMPLEMENTED");
    }

    @Test
    void vatInvoiceBlocksInvalidItemsAndDegradesHonestlyWhenUnconfigured() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MktConnectorHubService hub = new MktConnectorHubService(jdbc, new ObjectMapper(),
                new FieldEncryptionService(""), mock(MktAuditService.class));
        assertThat(hub.verifyVatInvoice(Map.of("invoiceNo", "26442000000011223344",
                "itemName", "*餐饮服务*餐费", "totalAmount", "10600.00")).get("verifyStatus")).isEqualTo("INVALID_RULE");
        assertThat(hub.verifyVatInvoice(Map.of("invoiceNo", "26442000000088990011",
                "itemName", "*现代服务*招商推广服务费", "totalAmount", "21200.00")).get("verifyStatus")).isEqualTo("STANDBY_MANUAL");
    }

    @Test
    void waybillSamplingUsesRealOutboundTableAndPercentOfEligibleOrders() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FieldEncryptionService crypto = new FieldEncryptionService(KEY_B64);
        String cipher = crypto.encrypt("{\"customer\":\"C123\",\"key\":\"K456789\",\"sampleRatePct\":\"2\"}");
        when(jdbc.queryForList(contains("connector_code = ?"), eq(1L), eq("KUAIDI100")))
                .thenReturn(List.of(row("KUAIDI100", 1, 0, 3000, 0, cipher)));
        when(jdbc.queryForObject(contains("source_type = 2"), eq(Integer.class), any(Object[].class))).thenReturn(300);
        MktConnectorHubService hub = new MktConnectorHubService(jdbc, new ObjectMapper(), crypto, mock(MktAuditService.class));

        Map<String, Object> res = hub.runWaybillSpotCheck(null, "2026-11", null);
        assertThat(res.get("eligibleCount")).isEqualTo(300);
        // 300 单 × 2% = 6 单抽样，排除已抽检 order_id，避免 ORDER BY RAND()
        verify(jdbc).queryForList(argThat(sql -> sql.contains("r.logistics_no AS trackingNo")
                && sql.contains("NOT EXISTS") && !sql.contains("RAND()")),
                eq(1L), isNull(), isNull(), any(), any(), eq(6));
    }

    @Test
    void pullOnlineLeadsValidatesEndpointBeforeDeductingMonthlyQuotaAndShopVerifyCanBeAdopted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FieldEncryptionService crypto = new FieldEncryptionService(KEY_B64);
        String missingEndpointCipher = crypto.encrypt("{\"appId\":\"cm_id_1234\",\"appSecret\":\"cm_sec_5678\"}");
        when(jdbc.queryForList(contains("connector_code = ?"), eq(1L), eq("CHANMAMA_API")))
                .thenReturn(List.of(row("CHANMAMA_API", 1, 1, 1000, 5, missingEndpointCipher)));
        MktConnectorHubService hub = new MktConnectorHubService(jdbc, new ObjectMapper(), crypto, mock(MktAuditService.class));

        assertThatThrownBy(() -> hub.pullOnlineLeads("CHANMAMA_API", "GZ", "个人洗护", mock(MktPublicSeaWashService.class), 1L))
                .isInstanceOf(BizException.class).hasMessageContaining("endpoint");
        verify(jdbc, never()).update(contains("month_used_count < monthly_quota_limit"), any(Object[].class));

        when(jdbc.queryForList(contains("FROM crm_shop_oauth_session"), eq(88L), eq(901L)))
                .thenReturn(List.of(Map.of("id", 88L, "platform", "DOUYIN", "shopName", "某店",
                        "status", "SELF_REPORTED", "verifiedDailyAvg", 1250, "avgWeightKg", "0.65")));
        Map<String, Object> adopted = hub.adoptShopVerification(901L, 88L);
        assertThat(adopted.get("adoptedDailyOrders")).isEqualTo(1250);
        assertThat(adopted.get("status")).isEqualTo("ADOPTED");
        verify(jdbc).update(contains("UPDATE crm_public_sea_meta"), eq(1250), eq(1250), any(), eq(1250), eq(901L));
    }
}
