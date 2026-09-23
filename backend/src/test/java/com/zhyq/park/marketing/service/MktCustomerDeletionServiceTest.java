package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.SysAudit;
import com.zhyq.park.marketing.mapper.SysAuditMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MktCustomerDeletionServiceTest {
    final CustomerMapper customers = mock(CustomerMapper.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final SysAuditMapper audits = mock(SysAuditMapper.class);
    final MktCustomerDeletionService service = new MktCustomerDeletionService(customers, jdbc, audits, new ObjectMapper());
    Customer customer;

    @BeforeEach void fixture() {
        customer = new Customer(); customer.setId(42L); customer.setName("测试推荐客户");
        customer.setReferrerId(7L); customer.setStatus(1); customer.setAssignedWarehouseId(9L);
        when(customers.selectForUpdate(42L)).thenReturn(customer);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(audits.insert(any(SysAudit.class))).thenReturn(1);
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void ownFollowingCustomerCanBeDeletedIncludingPendingOrAcceptedAssignmentAndOrdinaryLocks() {
        login("mp:7");
        service.deleteOwnReferral(42L);
        var order = inOrder(customers, jdbc, audits);
        order.verify(customers).selectForUpdate(42L);
        verify(jdbc).update(startsWith("UPDATE crm_customer SET deleted=1"), eq("mp:7"), eq(42L));
        verify(jdbc).update(contains("WHERE customer_id=? AND deleted=0 AND status IN (1,2)"),
                eq("园区伙伴删除自己推荐的客户"), eq("mp:7"), eq("mp:7"), eq(42L));
        verify(jdbc).update(startsWith("UPDATE crm_customer_lock SET deleted=1"), eq("mp:7"), eq(42L));
        ArgumentCaptor<SysAudit> captor = ArgumentCaptor.forClass(SysAudit.class);
        verify(audits).insert(captor.capture());
        SysAudit audit = captor.getValue();
        assertThat(audit.getAction()).isEqualTo("customer.delete");
        assertThat(audit.getOperator()).isEqualTo("mp:7");
        assertThat(audit.getBeforeJson()).contains("测试推荐客户", "\"referrerId\":7", "\"assignedWarehouseId\":9");
        assertThat(audit.getAfterJson()).contains("\"deleted\":1", "\"locksReleased\":true");
        verify(jdbc, never()).update(startsWith("DELETE"), any(Object[].class));
        verify(jdbc, never()).update(contains("crm_service_contract"), any(Object[].class));
        verify(jdbc, never()).update(contains("crm_referral_order"), any(Object[].class));
    }

    @Test void administratorCanDeleteLostReferralWithoutOwningIt() {
        login("admin"); customer.setStatus(3);
        service.deleteAsAdmin(42L);
        verify(jdbc).update(startsWith("UPDATE crm_customer SET deleted=1"), eq("admin"), eq(42L));
    }

    @Test void otherPartnerAndNonReferralAreRejectedBeforeAnyDependencyOrMutationAccess() {
        login("mp:8");
        assertThatThrownBy(() -> service.deleteOwnReferral(42L)).isInstanceOf(BizException.class).hasMessageContaining("自己推荐");
        login("mp:7"); customer.setReferrerId(null);
        assertThatThrownBy(() -> service.deleteOwnReferral(42L)).isInstanceOf(BizException.class).hasMessageContaining("自己推荐");
        verifyNoInteractions(jdbc, audits);
    }

    @Test void missingOrDeletedCustomerCannotBeDeletedAgainOrRevealPresenceToPartner() {
        when(customers.selectForUpdate(42L)).thenReturn(null);
        assertThatThrownBy(() -> service.deleteAsAdmin(42L)).hasMessageContaining("不存在或已删除");
        login("mp:7");
        assertThatThrownBy(() -> service.deleteOwnReferral(42L)).hasMessageContaining("无权删除");
        verifyNoInteractions(jdbc, audits);
    }

    @ParameterizedTest
    @CsvSource({"crm_service_contract,服务合同", "crm_referral_order,业务订单或佣金", "crm_customer_lock,成交记录", "crm_customer_erp_map,ERP"})
    void dependenciesBlockBeforeMutationForAdminAndPartner(String table, String hint) {
        when(jdbc.queryForList(startsWith("SELECT id FROM " + table + " WHERE"), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(99L));
        assertThatThrownBy(() -> service.deleteAsAdmin(42L)).hasMessageContaining(hint);
        login("mp:7");
        assertThatThrownBy(() -> service.deleteOwnReferral(42L)).hasMessageContaining(hint);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(audits);
    }

    @Test void linkedLeadFollowupAndSignedArchiveArePreserved() {
        customer.setSourceLeadId(18L);
        assertThatThrownBy(() -> service.deleteAsAdmin(42L)).hasMessageContaining("线索及跟进");
        customer.setSourceLeadId(null); customer.setStatus(2);
        assertThatThrownBy(() -> service.deleteAsAdmin(42L)).hasMessageContaining("已签约");
        verifyNoInteractions(jdbc, audits);
    }

    @Test void financialGuardsIncludeDeletedHistoryAndLockOnlyThisCustomer() {
        service.deleteAsAdmin(42L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(4)).queryForList(sql.capture(), eq(Long.class), eq(42L));
        assertThat(sql.getAllValues()).allSatisfy(query -> assertThat(query).contains("customer_id=?", "FOR UPDATE"));
        assertThat(sql.getAllValues().get(0)).doesNotContain("deleted=");
        assertThat(sql.getAllValues().get(1)).doesNotContain("deleted=");
        assertThat(sql.getAllValues().get(2)).contains("status=3");
        assertThat(sql.getAllValues().get(3)).contains("deleted=0");
    }

    @Test void ordinaryCrmDeleteCannotBypassReferralOrAssignmentBoundary() {
        assertThatThrownBy(() -> service.deleteOrdinary(42L)).hasMessageContaining("管理员");
        customer.setReferrerId(null);
        assertThatThrownBy(() -> service.deleteOrdinary(42L)).hasMessageContaining("管理员");
        verifyNoInteractions(jdbc, audits);
    }

    @Test void ordinaryCrmDeleteAlsoUsesFinancialGuards() {
        customer.setReferrerId(null); customer.setAssignedWarehouseId(null);
        when(jdbc.queryForList(startsWith("SELECT id FROM crm_referral_order WHERE"), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(99L));
        assertThatThrownBy(() -> service.deleteOrdinary(42L)).hasMessageContaining("业务订单或佣金");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void failedConditionalDeleteDoesNotReleaseLocksOrWriteAudit() {
        when(jdbc.update(startsWith("UPDATE crm_customer SET"), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.deleteAsAdmin(42L)).hasMessageContaining("已变化");
        verify(jdbc, never()).update(startsWith("UPDATE crm_customer_lock"), any(Object[].class));
        verifyNoInteractions(audits);
    }

    @Test void failedAuditMustThrowForTransactionRollback() {
        when(audits.insert(any(SysAudit.class))).thenReturn(0);
        assertThatThrownBy(() -> service.deleteAsAdmin(42L)).hasMessageContaining("审计保存失败");
    }

    static void login(String subject) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(subject, null, List.of()));
    }
}
