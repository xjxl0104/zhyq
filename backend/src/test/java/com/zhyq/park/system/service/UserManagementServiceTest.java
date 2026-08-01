package com.zhyq.park.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.system.dto.SysUserSaveRequest;
import com.zhyq.park.system.dto.UserRoleLabel;
import com.zhyq.park.system.entity.SysRole;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.entity.SysUserRole;
import com.zhyq.park.system.mapper.SysRoleMapper;
import com.zhyq.park.system.mapper.SysUserMapper;
import com.zhyq.park.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    private static final long USER_ID = 88L;

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private CurrentUserContext currentUserContext;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(
                userMapper, roleMapper, userRoleMapper, passwordEncoder, currentUserContext);
    }

    @Test
    void createUserHashesPasswordAndBindsMultipleRoles() {
        SysRole finance = role(2L, "finance", "财务人员", 1);
        SysRole property = role(3L, "pm_dispatch", "物业调度", 1);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(finance, property));
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(USER_ID);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        Long id = service.create(request(null, "new-user", "secret88", 1, List.of(2L, 3L)));

        assertEquals(USER_ID, id);
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(userCaptor.capture());
        assertTrue(passwordEncoder.matches("secret88", userCaptor.getValue().getPassword()));
        verify(userRoleMapper, times(2)).insert(any(SysUserRole.class));
    }

    @Test
    void superAdministratorCanCreateAnotherSuperAdministrator() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(adminRole()));
        when(currentUserContext.isAdmin()).thenReturn(true);
        doAnswer(invocation -> {
            ((SysUser) invocation.getArgument(0)).setId(USER_ID);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        assertDoesNotThrow(() -> service.create(
                request(null, "second-admin", "secret88", 1, List.of(1L))));
        verify(userRoleMapper).insert(any(SysUserRole.class));
    }

    @Test
    void nonAdministratorCannotGrantAdminRole() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(adminRole()));
        when(currentUserContext.isAdmin()).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.create(
                request(null, "forbidden-admin", "secret88", 1, List.of(1L))));

        assertTrue(ex.getMessage().contains("超级管理员"));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void nonAdministratorCannotEditExistingAdministrator() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser(1));
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(adminRole()));
        when(userRoleMapper.hasRoleCode(USER_ID, "admin")).thenReturn(1);
        when(currentUserContext.isAdmin()).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.update(
                request(USER_ID, "old-user", "new-secret", 1, List.of(1L))));

        assertTrue(ex.getMessage().contains("超级管理员"));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void updateReplacesRolesAndKeepsPasswordWhenBlank() {
        SysUser existing = existingUser(1);
        when(userMapper.selectById(USER_ID)).thenReturn(existing);
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role(2L, "finance", "财务人员", 1)));
        when(userRoleMapper.hasRoleCode(USER_ID, "admin")).thenReturn(0);

        service.update(request(USER_ID, "old-user", "", 1, List.of(2L)));

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertNull(userCaptor.getValue().getPassword());
        verify(userRoleMapper).delete(any(Wrapper.class));
        verify(userRoleMapper).insert(any(SysUserRole.class));
    }

    @Test
    void lastActiveAdministratorCannotBeDemoted() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser(1));
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role(2L, "finance", "财务人员", 1)));
        when(userRoleMapper.hasRoleCode(USER_ID, "admin")).thenReturn(1);
        when(currentUserContext.isAdmin()).thenReturn(true);
        when(userRoleMapper.countActiveAdminUsers()).thenReturn(1);

        assertThrows(BizException.class, () -> service.update(
                request(USER_ID, "old-user", "", 1, List.of(2L))));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void lastActiveAdministratorCannotBeDisabled() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser(1));
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(adminRole()));
        when(userRoleMapper.hasRoleCode(USER_ID, "admin")).thenReturn(1);
        when(currentUserContext.isAdmin()).thenReturn(true);
        when(userRoleMapper.countActiveAdminUsers()).thenReturn(1);

        assertThrows(BizException.class, () -> service.update(
                request(USER_ID, "old-user", "", 0, List.of(1L))));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void lastActiveAdministratorCannotBeDeleted() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser(1));
        when(userRoleMapper.hasRoleCode(USER_ID, "admin")).thenReturn(1);
        when(currentUserContext.isAdmin()).thenReturn(true);
        when(userRoleMapper.countActiveAdminUsers()).thenReturn(1);

        assertThrows(BizException.class, () -> service.delete(USER_ID));
        verify(userMapper, never()).deleteById(USER_ID);
    }

    @Test
    void inactiveAdministratorCanBeDeletedWhenAnotherActiveAdministratorExists() {
        when(userMapper.selectById(USER_ID)).thenReturn(existingUser(0));
        when(userRoleMapper.hasRoleCode(USER_ID, "admin")).thenReturn(1);
        when(currentUserContext.isAdmin()).thenReturn(true);

        assertDoesNotThrow(() -> service.delete(USER_ID));
        verify(userMapper).deleteById(USER_ID);
    }

    @Test
    void duplicateUsernameIsRejectedBeforeInsert() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.create(
                request(null, "existing", "secret88", 1, List.of(2L))));
        verify(roleMapper, never()).selectBatchIds(anyCollection());
    }

    @Test
    void disabledRoleIsRejected() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(role(2L, "finance", "财务人员", 0)));

        assertThrows(BizException.class, () -> service.create(
                request(null, "new-user", "secret88", 1, List.of(2L))));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void roleNamesAreFilledWithOneBatchQuery() {
        SysUser first = new SysUser();
        first.setId(1L);
        SysUser second = new SysUser();
        second.setId(2L);
        UserRoleLabel row = new UserRoleLabel();
        row.setUserId(1L);
        row.setRoleNames("财务人员,物业调度");
        when(userRoleMapper.selectRoleLabelsByUserIds(List.of(1L, 2L))).thenReturn(List.of(row));

        service.fillRoleNames(List.of(first, second));

        assertEquals(List.of("财务人员", "物业调度"), first.getRoleNames());
        assertEquals(List.of(), second.getRoleNames());
        verify(userRoleMapper).selectRoleLabelsByUserIds(List.of(1L, 2L));
    }

    private static SysUserSaveRequest request(Long id, String username, String password,
                                              Integer status, List<Long> roleIds) {
        SysUserSaveRequest request = new SysUserSaveRequest();
        request.setId(id);
        request.setUsername(username);
        request.setPassword(password);
        request.setNickname("测试用户");
        request.setStatus(status);
        request.setRoleIds(roleIds);
        return request;
    }

    private static SysUser existingUser(Integer status) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("old-user");
        user.setNickname("旧用户");
        user.setStatus(status);
        return user;
    }

    private static SysRole adminRole() {
        return role(1L, "admin", "平台超级管理员", 1);
    }

    private static SysRole role(Long id, String code, String name, Integer status) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName(name);
        role.setSort(id.intValue());
        role.setStatus(status);
        return role;
    }
}
