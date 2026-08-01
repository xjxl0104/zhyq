package com.zhyq.park.auth;

import com.zhyq.park.system.controller.SysRoleController;
import com.zhyq.park.system.dto.RolePermissionRequest;
import com.zhyq.park.system.mapper.SysRoleMapper;
import com.zhyq.park.system.service.RoleManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SysRoleControllerSecurityTest.TestBeans.class)
class SysRoleControllerSecurityTest {

    @EnableMethodSecurity
    @Configuration
    static class TestBeans {
        @Bean
        SysRoleMapper sysRoleMapper() {
            return mock(SysRoleMapper.class);
        }

        @Bean
        RoleManagementService roleManagementService() {
            return mock(RoleManagementService.class);
        }

        @Bean
        SysRoleController sysRoleController(SysRoleMapper mapper, RoleManagementService service) {
            return new SysRoleController(mapper, service);
        }
    }

    @Autowired
    private SysRoleController controller;
    @Autowired
    private SysRoleMapper roleMapper;
    @Autowired
    private RoleManagementService service;

    @BeforeEach
    void resetMocks() {
        reset(roleMapper, service);
    }

    @Test
    @WithMockUser(authorities = "system:role:query")
    void rolePermissionsCanBeQueriedWithRoleQueryPermission() {
        when(service.getMenuIds(2L)).thenReturn(List.of(10L, 11L));

        assertDoesNotThrow(() -> controller.menuIds(2L));
        verify(service).getMenuIds(2L);
    }

    @Test
    @WithMockUser(authorities = "system:role:edit")
    void rolePermissionSaveRejectsLegacyEditPermissionAlone() {
        RolePermissionRequest request = new RolePermissionRequest();
        request.setMenuIds(List.of(10L));

        assertThrows(AccessDeniedException.class,
                () -> controller.saveMenuIds(2L, request));
    }

    @Test
    @WithMockUser(authorities = "system:role:permission")
    void rolePermissionSaveUsesDedicatedPermission() {
        RolePermissionRequest request = new RolePermissionRequest();
        request.setMenuIds(List.of(10L, 11L));

        assertDoesNotThrow(() -> controller.saveMenuIds(2L, request));
        verify(service).saveMenuIds(2L, List.of(10L, 11L));
    }

    @Test
    @WithMockUser(authorities = "system:role:delete")
    void roleDeleteDelegatesToProtectedService() {
        assertDoesNotThrow(() -> controller.delete(2L));
        verify(service).delete(2L);
    }
}
