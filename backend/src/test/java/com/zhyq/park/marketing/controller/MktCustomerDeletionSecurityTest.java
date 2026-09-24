package com.zhyq.park.marketing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.SysAudit;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.mp.MpBizController;
import com.zhyq.park.marketing.service.*;
import com.zhyq.park.tenant.mapper.TenantMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MktCustomerDeletionSecurityTest.Beans.class)
class MktCustomerDeletionSecurityTest {
    @Configuration @EnableMethodSecurity @EnableTransactionManagement
    static class Beans {
        @Bean CustomerMapper customers() { return mock(CustomerMapper.class); }
        @Bean JdbcTemplate jdbc() { return mock(JdbcTemplate.class); }
        @Bean SysAuditMapper audits() { return mock(SysAuditMapper.class); }
        @Bean PlatformTransactionManager transactionManager() { return mock(PlatformTransactionManager.class); }
        @Bean MktCustomerDeletionService deletion(CustomerMapper customers, JdbcTemplate jdbc, SysAuditMapper audits) {
            return new MktCustomerDeletionService(customers, jdbc, audits, new ObjectMapper());
        }
        @Bean MktCustomerController admin(CustomerMapper customers, MktCustomerDeletionService deletion) {
            return new MktCustomerController(customers, mock(MktPromoterMapper.class), mock(MktCustomerGradeMapper.class),
                    mock(MktServiceContractMapper.class), mock(MktLockService.class), mock(MktAuditService.class),
                    mock(MktCustomerAssignmentService.class), deletion);
        }
        @Bean MpBizController partner(CustomerMapper customers, MktCustomerDeletionService deletion) {
            return new MpBizController(customers, mock(MktPromoterMapper.class), mock(MktWarehouseMapper.class),
                    mock(MktPromoterCommissionMapper.class), mock(MktReferralOrderMapper.class), mock(MktWithdrawalMapper.class),
                    mock(TenantMessageMapper.class), mock(MktLockService.class), mock(MktWithdrawalService.class),
                    mock(BizSettings.class), mock(MktAuditService.class), mock(MktCustomerAssignmentService.class), deletion);
        }
    }

    @Autowired MktCustomerController admin;
    @Autowired MpBizController partner;
    @Autowired MktCustomerDeletionService deletion;
    @Autowired CustomerMapper customers;
    @Autowired JdbcTemplate jdbc;
    @Autowired SysAuditMapper audits;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void fixture() {
        reset(customers, jdbc, audits, transactions);
        Customer c = new Customer(); c.setId(42L); c.setReferrerId(7L); c.setStatus(1);
        when(customers.selectForUpdate(42L)).thenReturn(c);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(audits.insert(any(SysAudit.class))).thenReturn(1);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test @WithMockUser(roles = "admin")
    void administratorCanDeleteWithoutGeneralEditAuthorityAndTransactionCommits() {
        assertThat(admin.delete(42L).getCode()).isZero();
        verify(customers).selectForUpdate(42L);
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
    }

    @Test @WithMockUser(authorities = {"crm:marketing:customer:edit", "crm:marketing:customer:delete", "crm:customer:delete"})
    void editOrInventedDeleteAuthorityDoesNotGrantAdminDelete() {
        assertThatThrownBy(() -> admin.delete(42L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> deletion.deleteAsAdmin(42L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(customers, jdbc, audits);
    }

    @Test @WithMockUser(username = "mp:7", roles = "MP")
    void ownerCanUseMiniProgramDeleteButNotAdminDelete() {
        assertThatThrownBy(() -> admin.delete(42L)).isInstanceOf(AccessDeniedException.class);
        assertThat(partner.deleteCustomer(42L).getCode()).isZero();
        verify(jdbc).update(startsWith("UPDATE crm_customer SET"), eq("mp:7"), eq(42L));
    }

    @Test @WithMockUser(username = "mp:8", roles = "MP")
    void authenticatedDifferentPartnerCannotDeleteAnotherPartnersReferral() {
        assertThatThrownBy(() -> partner.deleteCustomer(42L)).isInstanceOf(BizException.class).hasMessageContaining("自己推荐");
        verifyNoInteractions(jdbc, audits);
        verify(transactions).rollback(any());
    }

    @Test @WithMockUser(username = "wh:7", roles = "WH")
    void warehouseIdentityCannotUseEitherDeleteEntryPoint() {
        assertThatThrownBy(() -> admin.delete(42L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> partner.deleteCustomer(42L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(customers, jdbc, audits);
    }

    @Test @WithMockUser(roles = "admin")
    void administrativeTokenCannotImpersonatePartner() {
        assertThatThrownBy(() -> partner.deleteCustomer(42L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(customers, jdbc, audits);
    }

    @Test @WithMockUser(roles = "admin")
    void auditFailureRollsBackEntireDeletionTransaction() {
        when(audits.insert(any(SysAudit.class))).thenReturn(0);
        assertThatThrownBy(() -> admin.delete(42L)).hasMessageContaining("审计保存失败");
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }
}
