package com.zhyq.park.marketing.service;

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
}
