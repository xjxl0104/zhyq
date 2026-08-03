package com.zhyq.park.vending;

import com.zhyq.park.vending.controller.VendingController;
import com.zhyq.park.vending.mapper.VendingFaultMapper;
import com.zhyq.park.vending.mapper.VendingMachineMapper;
import com.zhyq.park.vending.mapper.VendingReconciliationMapper;
import com.zhyq.park.vending.mapper.VendingRestockMapper;
import com.zhyq.park.vending.mapper.VendingSaleMapper;
import com.zhyq.park.vending.service.VendingImportService;
import com.zhyq.park.vending.service.VendingTemplateService;
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
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VendingControllerSecurityTest.TestBeans.class)
class VendingControllerSecurityTest {
    @EnableMethodSecurity
    @Configuration
    static class TestBeans {
        @Bean VendingMachineMapper machineMapper() { return mock(VendingMachineMapper.class); }
        @Bean VendingSaleMapper saleMapper() { return mock(VendingSaleMapper.class); }
        @Bean VendingRestockMapper restockMapper() { return mock(VendingRestockMapper.class); }
        @Bean VendingFaultMapper faultMapper() { return mock(VendingFaultMapper.class); }
        @Bean VendingReconciliationMapper reconciliationMapper() { return mock(VendingReconciliationMapper.class); }
        @Bean VendingImportService importService() { return mock(VendingImportService.class); }
        @Bean VendingTemplateService templateService() { return mock(VendingTemplateService.class); }
        @Bean VendingController controller(
                VendingMachineMapper machines, VendingSaleMapper sales,
                VendingRestockMapper restocks, VendingFaultMapper faults,
                VendingReconciliationMapper reconciliations, VendingImportService imports,
                VendingTemplateService templates) {
            return new VendingController(machines, sales, restocks, faults, reconciliations,
                    imports, templates, "https://fanmaiji.top/index?isFrom=login");
        }
    }

    @Autowired private VendingController controller;

    @Test
    @WithMockUser(authorities = "finance:bill:query")
    void unrelatedPermissionCannotUseAnyVendingBoundary() {
        var file = new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[]{1});
        assertThrows(AccessDeniedException.class, () -> controller.stats());
        assertThrows(AccessDeniedException.class, () -> controller.template("MACHINE"));
        assertThrows(AccessDeniedException.class, () -> controller.preview("MACHINE", file));
        assertThrows(AccessDeniedException.class, () -> controller.config());
        assertThrows(AccessDeniedException.class, () -> controller.configurationStatus());
    }

    @Test
    @WithMockUser(authorities = "vending:query")
    void exactQueryPermissionOnlyAllowsQueries() {
        assertDoesNotThrow(() -> controller.stats());
        assertThrows(AccessDeniedException.class, () -> controller.config());
    }

    @Test
    @WithMockUser(authorities = "vending:import")
    void exactImportPermissionAllowsTemplates() {
        assertDoesNotThrow(() -> controller.template("MACHINE"));
        assertThrows(AccessDeniedException.class, () -> controller.stats());
    }

    @Test
    @WithMockUser(authorities = "vending:open")
    void exactOpenPermissionAllowsExternalConfigAndAudit() {
        assertDoesNotThrow(() -> controller.config());
        assertDoesNotThrow(() -> controller.openAudit());
        assertThrows(AccessDeniedException.class, () -> controller.configurationStatus());
    }

    @Test
    @WithMockUser(authorities = "vending:config")
    void exactConfigPermissionAllowsConfigurationStatusOnly() {
        assertDoesNotThrow(() -> controller.configurationStatus());
        assertThrows(AccessDeniedException.class, () -> controller.config());
    }
}
