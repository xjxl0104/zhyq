package com.zhyq.park.auth;

import com.zhyq.park.system.controller.SysUserController;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 系统用户管理 RBAC 单测:验证删除/新增等敏感操作被权限标识卡住。
 * admin 全通(拥有 system:user:delete),普通查询权限不足以删除。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SysUserControllerSecurityTest.TestBeans.class)
class SysUserControllerSecurityTest {

    @EnableMethodSecurity
    @Configuration
    static class TestBeans {
        @Bean SysUserMapper sysUserMapper() { return mock(SysUserMapper.class); }
        @Bean SysUserController sysUserController(SysUserMapper m) { return new SysUserController(m); }
    }

    @Autowired SysUserController controller;
    @Autowired SysUserMapper userMapper;

    @Test
    @WithMockUser(authorities = "system:user:query") // 只有查询权限
    void 删除用户_仅查询权限_拒绝() {
        assertThrows(AccessDeniedException.class, () -> controller.delete(1L));
    }

    @Test
    @WithMockUser(authorities = "system:user:delete")
    void 删除用户_有删除权限_放行() {
        when(userMapper.deleteById(any(Long.class))).thenReturn(1);
        assertDoesNotThrow(() -> controller.delete(1L));
    }

    @Test
    @WithMockUser(authorities = "contract:add") // 完全不相关的权限
    void 新增用户_无关权限_拒绝() {
        assertThrows(AccessDeniedException.class, () -> controller.add(new SysUser()));
    }

    @Test
    @WithMockUser(authorities = "system:user:add")
    void 新增用户_有新增权限_放行() {
        when(userMapper.insert(any(SysUser.class))).thenReturn(1);
        assertDoesNotThrow(() -> controller.add(new SysUser()));
    }
}
