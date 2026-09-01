package com.zhyq.park.receivable;

import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.importing.mapper.ImportBatchMapper;
import com.zhyq.park.importing.mapper.ImportRowMapper;
import com.zhyq.park.receivable.controller.ReceivableController;
import com.zhyq.park.receivable.mapper.CollectionAccountMapper;
import com.zhyq.park.receivable.mapper.DepositLedgerMapper;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import com.zhyq.park.receivable.mapper.ReceivableRuleMapper;
import com.zhyq.park.receivable.entity.CollectionAccount;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import com.zhyq.park.receivable.service.ReceivableCalculator;
import com.zhyq.park.receivable.service.ReceivableExportService;
import com.zhyq.park.receivable.service.ReceivableImportService;
import com.zhyq.park.receivable.service.ReceivablePlanService;
import com.zhyq.park.receivable.service.ReceivableProvisionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ReceivableControllerSecurityTest.TestBeans.class)
class ReceivableControllerSecurityTest {
    @EnableMethodSecurity
    @Configuration
    static class TestBeans {
        @Bean ReceivableRegisterMapper registerMapper() { return mock(ReceivableRegisterMapper.class); }
        @Bean ReceivableRuleMapper ruleMapper() { return mock(ReceivableRuleMapper.class); }
        @Bean DepositLedgerMapper depositMapper() { return mock(DepositLedgerMapper.class); }
        @Bean BillMapper billMapper() { return mock(BillMapper.class); }
        @Bean CollectionAccountMapper accountMapper() { return mock(CollectionAccountMapper.class); }
        @Bean ReceivableImportService importService() { return mock(ReceivableImportService.class); }
        @Bean ReceivablePlanService planService() { return mock(ReceivablePlanService.class); }
        @Bean ReceivableProvisionService provisionService() { return mock(ReceivableProvisionService.class); }
        @Bean ReceivableExportService exportService() { return mock(ReceivableExportService.class); }
        @Bean FieldEncryptionService encryptionService() { return mock(FieldEncryptionService.class); }
        @Bean ImportBatchMapper batchMapper() { return mock(ImportBatchMapper.class); }
        @Bean ImportRowMapper importRowMapper() { return mock(ImportRowMapper.class); }
        @Bean ReceivableCalculator calculator() { return mock(ReceivableCalculator.class); }
        @Bean com.zhyq.park.receivable.service.ReceivableAutoBillService autoBillService() {
            return mock(com.zhyq.park.receivable.service.ReceivableAutoBillService.class);
        }
        @Bean ReceivableController controller(
                ReceivableRegisterMapper registers, ReceivableRuleMapper rules,
                DepositLedgerMapper deposits, BillMapper bills, CollectionAccountMapper accounts,
                ReceivableImportService imports, ReceivablePlanService plans,
                com.zhyq.park.receivable.service.ReceivableAutoBillService autoBills,
                ReceivableProvisionService provisions,
                ReceivableExportService exports, FieldEncryptionService encryption,
                ImportBatchMapper batches, ImportRowMapper rows,
                ReceivableCalculator calculator) {
            return new ReceivableController(registers, rules, deposits, bills, accounts,
                    imports, plans, autoBills, provisions, exports, encryption, batches, rows, calculator);
        }
    }

    @Autowired private ReceivableController controller;
    @Autowired private CollectionAccountMapper accountMapper;
    @Autowired private FieldEncryptionService encryptionService;

    @Test
    @WithMockUser(authorities = "finance:bill:query")
    void wrongPermissionCannotUseReceivableOperations() {
        MockMultipartFile file = new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[]{1});
        assertThrows(AccessDeniedException.class, () -> controller.get(1L));
        assertThrows(AccessDeniedException.class, () -> controller.preview(file));
        assertThrows(AccessDeniedException.class, () -> controller.confirm(1L));
        assertThrows(AccessDeniedException.class, () -> controller.generate(1L));
        assertThrows(AccessDeniedException.class, () -> controller.revealAccount(1L));
    }

    @Test
    @WithMockUser(authorities = "finance:receivable:query")
    void exactQueryPermissionIsAllowed() {
        assertDoesNotThrow(() -> controller.get(1L));
        var capabilities = controller.capabilities().getData();
        assertTrue(capabilities.query());
        assertFalse(capabilities.importData());
        assertFalse(capabilities.confirm());
    }

    @Test
    @WithMockUser(authorities = "finance:receivable:import")
    void exactImportPermissionIsAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[]{1});
        assertDoesNotThrow(() -> controller.preview(file));
    }

    @Test
    @WithMockUser(authorities = "finance:receivable:confirm")
    void exactConfirmPermissionIsAllowed() {
        assertDoesNotThrow(() -> controller.confirm(1L));
    }

    @Test
    @WithMockUser(authorities = "finance:receivable:generate")
    void exactGeneratePermissionIsAllowed() {
        assertDoesNotThrow(() -> controller.generate(1L));
    }

    @Test
    @WithMockUser(authorities = "finance:receivable:account:view")
    void exactAccountPermissionIsAllowed() {
        CollectionAccount account = new CollectionAccount();
        account.setAccountNoCipher("v1:test");
        when(accountMapper.selectById(1L)).thenReturn(account);
        when(encryptionService.decrypt("v1:test")).thenReturn("6222****0001");
        assertDoesNotThrow(() -> controller.revealAccount(1L));
    }
}
