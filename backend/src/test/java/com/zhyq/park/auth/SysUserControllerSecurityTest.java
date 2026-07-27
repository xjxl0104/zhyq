package com.zhyq.park.auth;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.system.controller.SysUserController;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 系统用户管理 RBAC + 密码通道单测:
 * - 敏感操作被权限标识卡住(admin 全通,普通查询权限不足以删除)
 * - 新增用户密码 BCrypt 入库,缺密码拒绝;编辑留空不覆盖原密码
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SysUserControllerSecurityTest.TestBeans.class)
class SysUserControllerSecurityTest {

    @EnableMethodSecurity
    @Configuration
    static class TestBeans {
        @Bean SysUserMapper sysUserMapper() { return mock(SysUserMapper.class); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
        @Bean SysUserController sysUserController(SysUserMapper m, PasswordEncoder e) {
            return new SysUserController(m, e);
        }
    }

    @Autowired SysUserController controller;
    @Autowired SysUserMapper userMapper;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetSharedMock() {
        // mock 是 Spring 单例,跨用例共享调用记录,verify 前必须清零
        reset(userMapper);
    }

    private static SysUser userWithPassword(String pwd) {
        SysUser u = new SysUser();
        u.setUsername("tester");
        u.setPassword(pwd);
        return u;
    }

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
        assertThrows(AccessDeniedException.class, () -> controller.add(userWithPassword("abc123456")));
    }

    @Test
    @WithMockUser(authorities = "system:user:add")
    void 新增用户_有新增权限_放行() {
        when(userMapper.insert(any(SysUser.class))).thenReturn(1);
        assertDoesNotThrow(() -> controller.add(userWithPassword("abc123456")));
    }

    @Test
    @WithMockUser(authorities = "system:user:add")
    void 新增用户_密码BCrypt入库() {
        when(userMapper.insert(any(SysUser.class))).thenReturn(1);
        controller.add(userWithPassword("abc123456"));
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        String stored = captor.getValue().getPassword();
        assertTrue(stored.startsWith("$2"), "应为 BCrypt 哈希而非明文");
        assertTrue(passwordEncoder.matches("abc123456", stored));
    }

    @Test
    @WithMockUser(authorities = "system:user:add")
    void 新增用户_缺密码_拒绝() {
        assertThrows(BizException.class, () -> controller.add(userWithPassword("  ")));
    }

    @Test
    @WithMockUser(authorities = "system:user:edit")
    void 修改用户_密码留空_不覆盖原密码() {
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
        SysUser u = userWithPassword("");
        u.setId(1L);
        controller.update(u);
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertNull(captor.getValue().getPassword(), "空密码应置 null,由 MP 忽略该字段");
    }

    @Test
    @WithMockUser(authorities = "system:user:edit")
    void 修改用户_填新密码_BCrypt重置() {
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
        SysUser u = userWithPassword("newPass888");
        u.setId(1L);
        controller.update(u);
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertTrue(passwordEncoder.matches("newPass888", captor.getValue().getPassword()));
    }
}
