package com.zhyq.park.auth;

import com.zhyq.park.system.controller.SysUserController;
import com.zhyq.park.system.dto.SysUserDetailResponse;
import com.zhyq.park.system.dto.SysUserSaveRequest;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import com.zhyq.park.system.service.UserManagementService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SysUserControllerSecurityTest.TestBeans.class)
class SysUserControllerSecurityTest {

    @EnableMethodSecurity
    @Configuration
    static class TestBeans {
        @Bean
        SysUserMapper sysUserMapper() {
            return mock(SysUserMapper.class);
        }

        @Bean
        UserManagementService userManagementService() {
            return mock(UserManagementService.class);
        }

        @Bean
        SysUserController sysUserController(SysUserMapper mapper, UserManagementService service) {
            return new SysUserController(mapper, service);
        }
    }

    @Autowired
    private SysUserController controller;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private UserManagementService service;

    @BeforeEach
    void resetMocks() {
        reset(userMapper, service);
    }

    @Test
    @WithMockUser(authorities = "system:user:add")
    void createUserWithoutRoleAssignmentPermissionIsDenied() {
        assertThrows(AccessDeniedException.class,
                () -> controller.add(new SysUserSaveRequest()));
    }

    @Test
    @WithMockUser(authorities = {"system:user:add", "system:user:role"})
    void createUserWithBothPermissionsIsAllowed() {
        SysUserSaveRequest request = new SysUserSaveRequest();
        when(service.create(request)).thenReturn(9L);

        assertDoesNotThrow(() -> controller.add(request));
        verify(service).create(request);
    }

    @Test
    @WithMockUser(authorities = "system:user:edit")
    void updateUserWithoutRoleAssignmentPermissionIsDenied() {
        assertThrows(AccessDeniedException.class,
                () -> controller.update(new SysUserSaveRequest()));
    }

    @Test
    @WithMockUser(authorities = {"system:user:edit", "system:user:role"})
    void updateUserWithBothPermissionsIsAllowed() {
        SysUserSaveRequest request = new SysUserSaveRequest();

        assertDoesNotThrow(() -> controller.update(request));
        verify(service).update(request);
    }

    @Test
    @WithMockUser(authorities = "system:user:query")
    void userDetailsDelegateToManagementService() {
        SysUser user = new SysUser();
        user.setId(7L);
        when(service.get(7L)).thenReturn(new SysUserDetailResponse(user, List.of(1L, 2L)));

        assertDoesNotThrow(() -> controller.get(7L));
        verify(service).get(7L);
    }

    @Test
    @WithMockUser(authorities = "system:user:delete")
    void deleteUserDelegatesToProtectedService() {
        assertDoesNotThrow(() -> controller.delete(7L));
        verify(service).delete(7L);
    }

    @Test
    @WithMockUser(authorities = "system:user:query")
    void listUserRequiresQueryPermission() {
        when(userMapper.selectList(any())).thenReturn(List.of());
        assertDoesNotThrow(() -> controller.list());
    }

    @Test
    @WithMockUser(authorities = "contract:add")
    void unrelatedPermissionCannotDeleteUser() {
        assertThrows(AccessDeniedException.class, () -> controller.delete(7L));
    }
}
