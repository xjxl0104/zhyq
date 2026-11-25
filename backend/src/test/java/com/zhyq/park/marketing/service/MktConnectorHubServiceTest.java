package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.auth.BusinessRouteAccess;
import com.zhyq.park.marketing.mapper.MktPublicSeaMetaMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 18 通道热插拔外部数据连接器中心：权限隔离、AES-GCM 密钥保护、月度限额熔断与 6% 专票规则预检回归单测。
 */
class MktConnectorHubServiceTest {

    @Test
    void connectorRoutesBlockExternalMpRoleAndAllowInternalRoles() {
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/connectors/overview", "GET", Set.of("ROLE_MP"))).isFalse();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/connectors/QCC_PRO", "PUT", Set.of("ROLE_MP"))).isFalse();

        assertThat(BusinessRouteAccess.allowed("/crm/marketing/connectors/overview", "GET",
                Set.of("crm:marketing:setting:query"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/connectors/waybill-spot-check", "POST",
                Set.of("crm:marketing:settlement:edit"))).isTrue();
        assertThat(BusinessRouteAccess.allowed("/crm/marketing/connectors/invoice-verify", "POST",
                Set.of("crm:marketing:withdrawal:audit"))).isTrue();
    }

    @Test
    void overviewMergesEighteenDefaultConnectorsAndMonthlyQuotaFuseProtectsBudget() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktAuditService audit = mock(MktAuditService.class);
        String keyB64 = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        FieldEncryptionService crypto = new FieldEncryptionService(keyB64);

        // 模拟 QCC_PRO 当月调用已满 2000/2000 次 -> 自动触发 QUOTA_FUSED 熔断
        String cipher = crypto.encrypt("{\"appKey\":\"qcc_ak_123456\",\"secretKey\":\"qcc_sk_987654\"}");
        when(jdbc.queryForList(contains("FROM crm_ext_connector_config"), eq(1L))).thenReturn(List.of(
                Map.of(
                        "connector_code", "QCC_PRO",
                        "enabled", 1,
                        "gd_lock_only", 1,
                        "monthly_quota_limit", 2000,
                        "usage_month", YearMonth.now().toString(),
                        "month_used_count", 2000,
                        "blocked_out_gd_count", 15,
                        "cache_hit_count", 40,
                        "credentials_cipher", cipher,
                        "last_health_status", "OK"
                )
        ));

        MktConnectorHubService hub = new MktConnectorHubService(jdbc, new ObjectMapper(), crypto, seaMetas, audit);
        Map<String, Object> overview = hub.hubOverview();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) overview.get("connectors");
        assertThat(list).hasSize(18);

        Map<String, Object> qcc = list.stream().filter(m -> "QCC_PRO".equals(m.get("connectorCode"))).findFirst().orElseThrow();
        assertThat(qcc.get("healthStatus")).isEqualTo("QUOTA_FUSED");
        @SuppressWarnings("unchecked")
        Map<String, String> masked = (Map<String, String>) qcc.get("credentials");
        assertThat(masked.get("appKey")).isEqualTo("qcc****456");

        // 熔断后尝试获取调用配额必须返回 empty，绝不超支外发
        assertThat(hub.acquireActiveCredentials(1L, "QCC_PRO", false)).isEmpty();
    }

    @Test
    void vatInvoiceVerifierBlocksInvalidItemsAndStandbyDegradesGracefullyWhenUnconfigured() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MktPublicSeaMetaMapper seaMetas = mock(MktPublicSeaMetaMapper.class);
        MktAuditService audit = mock(MktAuditService.class);
        FieldEncryptionService crypto = new FieldEncryptionService("");

        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), anyString())).thenReturn(List.of());

        MktConnectorHubService hub = new MktConnectorHubService(jdbc, new ObjectMapper(), crypto, seaMetas, audit);

        // 非现代服务品目（如餐饮办公用品）直接拦截
        Map<String, Object> badItem = hub.verifyVatInvoice(Map.of(
                "invoiceNo", "26442000000011223344",
                "itemName", "*餐饮服务*餐费",
                "totalAmount", "10600.00"
        ));
        assertThat(badItem.get("verifyStatus")).isEqualTo("INVALID_RULE");

        // 合规现代服务品目且未填税局 Key 时，诚实返回 STANDBY_MANUAL 供财务复核版式
        Map<String, Object> okStandby = hub.verifyVatInvoice(Map.of(
                "invoiceNo", "26442000000088990011",
                "itemName", "*现代服务*招商推广服务费",
                "totalAmount", "21200.00"
        ));
        assertThat(okStandby.get("verifyStatus")).isEqualTo("STANDBY_MANUAL");
    }
}
