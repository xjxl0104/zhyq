package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerLock;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises public token attribution through the real referral service before any customer write. */
class MktPublicReferralWarehouseTenantTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final CustomerMapper customers = mock(CustomerMapper.class);
    final MktPromoterMapper partners = mock(MktPromoterMapper.class);
    final MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
    final MktLockService locks = mock(MktLockService.class);
    final BizSettings settings = mock(BizSettings.class);
    final MktAuditService audit = mock(MktAuditService.class);
    final MktCustomerAssignmentService assignment = mock(MktCustomerAssignmentService.class);
    final MktCustomerIntakeGuard guard = mock(MktCustomerIntakeGuard.class);
    final MktCustomerProgressService progress = mock(MktCustomerProgressService.class);
    final MktReferralService referrals = new MktReferralService(customers, partners, locks, settings, audit, assignment, guard, progress);
    final MktReferralLinkService links = new MktReferralLinkService(jdbc, partners, warehouses, referrals, new ObjectMapper());
    final String token = "0123456789abcdef0123456789abcdef";
    final MktPromoter partner = new MktPromoter();
    final MktWarehouse warehouse = new MktWarehouse();
    final Map<String,Object> body = new HashMap<>(Map.of("name", "扫码客户", "warehouseId", 88L,
            "submissionId", "request-0123456789", "agreed", true));

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Customer.class);
    }

    @BeforeEach void setup() {
        partner.setId(7L); partner.setTenantId(2L); partner.setStatus(1);
        // Neither side has projectId: a project-only guard previously allowed this tenant crossing.
        warehouse.setId(88L); warehouse.setTenantId(2L);
        when(jdbc.query(startsWith("SELECT promoter_id"), any(RowMapper.class), eq(token))).thenReturn(List.of(7L));
        when(partners.selectForUpdate(7L)).thenReturn(partner);
        when(settings.getInt("marketing", "daily_referral_cap", 20)).thenReturn(20);
        when(assignment.requireAvailableWarehouse(88L, null)).thenReturn(warehouse);
        when(customers.insert(any(Customer.class))).thenAnswer(invocation -> {
            ((Customer) invocation.getArgument(0)).setId(42L); return 1;
        });
        MktCustomerLock lock = new MktCustomerLock(); lock.setStatus(2);
        when(locks.prelock(42L, 7L)).thenReturn(lock);
    }

    static Stream<Long[]> sameTenants() {
        return Stream.of(new Long[]{2L, 2L, 2L}, new Long[]{1L, 1L, 1L},
                new Long[]{null, 1L, 1L}, new Long[]{1L, null, 1L}, new Long[]{null, null, 1L});
    }

    @ParameterizedTest @MethodSource("sameTenants")
    void sameTenantAndLegacyDefaultTenantsCanSubmit(Long partnerTenant, Long warehouseTenant, Long effectiveTenant) {
        partner.setTenantId(partnerTenant); warehouse.setTenantId(warehouseTenant);
        // An anonymous caller cannot choose the customer tenant instead of the token's partner.
        body.put("tenantId", 999L);
        assertThat(links.submit(token, body).get("customerId")).isEqualTo(42L);
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).insert(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(effectiveTenant);
        assertThat(saved.getValue().getIntendedWarehouseId()).isEqualTo(88L);
        assertThat(saved.getValue().getReferrerId()).isEqualTo(7L);
        verify(guard).lock(effectiveTenant);
        verify(locks).prelock(42L, 7L);
        verify(jdbc).update(startsWith("INSERT INTO mkt_customer_referral_submission"), eq(token),
                eq("request-0123456789"), anyString(), anyString());
    }

    static Stream<Long[]> foreignTenants() {
        return Stream.of(new Long[]{2L, 3L}, new Long[]{2L, null}, new Long[]{null, 2L});
    }

    @ParameterizedTest @MethodSource("foreignTenants")
    void foreignTenantWarehouseIsRejectedEvenWithoutProjects(Long partnerTenant, Long warehouseTenant) {
        partner.setTenantId(partnerTenant); warehouse.setTenantId(warehouseTenant);
        body.put("tenantId", warehouseTenant == null ? 1L : warehouseTenant);
        assertThatThrownBy(() -> links.submit(token, body)).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode()).isEqualTo(403)).hasMessageContaining("不属于同一运营主体");
        verify(customers, never()).insert(any(Customer.class));
        verifyNoInteractions(locks, progress, audit);
        verify(jdbc, never()).update(startsWith("INSERT INTO mkt_customer_referral_submission"), any(Object[].class));
    }

    @Test void noIntendedWarehouseStillCreatesReferral() {
        body.remove("warehouseId");
        assertThat(links.submit(token, body).get("customerId")).isEqualTo(42L);
        verifyNoInteractions(assignment);
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).insert(saved.capture());
        assertThat(saved.getValue().getIntendedWarehouseId()).isNull();
    }

    static Stream<Map<String,Object>> invalidFields() {
        return Stream.of(Map.of("name", " "), Map.of("phone", "123"),
                Map.of("requirements", Map.of("productCategories", List.of("其他"))),
                Map.of("requirements", Map.of("skuCount", "not-a-range")),
                Map.of("requirements", Map.of("productWeights", List.of(Map.of("range", ">=3", "percentage", 90)))),
                Map.of("serviceType", 9), Map.of("warehouseId", "bad-id"));
    }

    @ParameterizedTest @MethodSource("invalidFields")
    void rejectedInputReturnsEditableErrorWithoutCreatingCustomerOrReceipt(Map<String,Object> changes) {
        body.putAll(changes);
        assertThatThrownBy(() -> links.submit(token, body)).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode()).isEqualTo(400));
        verify(customers, never()).insert(any(Customer.class));
        verifyNoInteractions(locks, progress, audit);
        verify(jdbc, never()).update(startsWith("INSERT INTO mkt_customer_referral_submission"), any(Object[].class));
    }

    @Test void selfReferralRejectionLetsCustomerCorrectPhone() {
        partner.setPhone("13800000007");
        body.put("phone", "13800000007");
        assertThatThrownBy(() -> links.submit(token, body)).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode()).isEqualTo(400)).hasMessageContaining("自我推荐");
        verify(customers, never()).insert(any(Customer.class));
        body.put("phone", "13800000008");
        assertThat(links.submit(token, body).get("customerId")).isEqualTo(42L);
        verify(customers, times(1)).insert(any(Customer.class));
    }

    @Test void existingCustomerAndDailyQuotaAreExplicitRejections() {
        when(customers.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> links.submit(token, body)).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode()).isEqualTo(409));
        when(customers.selectCount(any())).thenReturn(0L);
        when(customers.countReferralsSince(eq(7L), any())).thenReturn(20L);
        assertThatThrownBy(() -> links.submit(token, body)).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode()).isEqualTo(429));
        verify(customers, never()).insert(any(Customer.class));
        verify(jdbc, never()).update(startsWith("INSERT INTO mkt_customer_referral_submission"), any(Object[].class));
    }

    @Test void publicSeaCustomerWithoutReferrerCanBeReclaimedByAnotherPartnerWhileProtectedOrSignedCannot() {
        Customer publicSea = new Customer();
        publicSea.setId(66L); publicSea.setTenantId(2L); publicSea.setName("扫码客户"); publicSea.setReferrerId(null); publicSea.setStatus(1);
        when(customers.selectCount(any())).thenReturn(1L);
        when(customers.selectOne(any())).thenReturn(publicSea);
        MktCustomerLock newLock = new MktCustomerLock();
        newLock.setStatus(MktLockService.LS_LOCKED);
        newLock.setLockUntil(java.time.LocalDateTime.now().plusMonths(3));
        when(locks.prelock(66L, 7L)).thenReturn(newLock);
        body.put("contact", "新联系人"); body.put("remark", "新需求补充");

        Map<String, Object> res = referrals.create(7L, body);
        assertThat(res.get("customerId")).isEqualTo(66L);
        assertThat(res.get("lockStatus")).isEqualTo(MktLockService.LS_LOCKED);
        assertThat(res.get("lockUntil")).isEqualTo(newLock.getLockUntil());
        verify(assignment).assertNoActiveContracts(66L);
        verify(locks).prelock(66L, 7L);
        verify(customers).update(isNull(), any());
        verify(audit).log(eq("referral.reclaim"), eq("customer"), eq(66L), contains("重报接管"));
        verify(customers, never()).insert(any(Customer.class));

        // 仍在他人保护期内(referrerId != null)或已签约成交(status == 2)时仍抛 409
        publicSea.setReferrerId(99L);
        assertThatThrownBy(() -> referrals.create(7L, body)).isInstanceOfSatisfying(BizException.class,
                err -> assertThat(err.getCode()).isEqualTo(409));
        publicSea.setReferrerId(null); publicSea.setStatus(2);
        assertThatThrownBy(() -> referrals.create(7L, body)).isInstanceOfSatisfying(BizException.class,
                err -> assertThat(err.getCode()).isEqualTo(409));

        // 原伙伴在 30 天冷却期内重报由 prelock 拒绝
        publicSea.setStatus(1);
        when(locks.prelock(66L, 7L)).thenThrow(new BizException("该客户释放后 30 天内不可再次报备"));
        assertThatThrownBy(() -> referrals.create(7L, body))
                .isInstanceOf(BizException.class).hasMessageContaining("30 天内不可再次报备");
    }
}
