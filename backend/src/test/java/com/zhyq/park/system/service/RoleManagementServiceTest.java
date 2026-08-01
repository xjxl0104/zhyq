package com.zhyq.park.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.system.entity.SysMenu;
import com.zhyq.park.system.entity.SysRole;
import com.zhyq.park.system.entity.SysRoleMenu;
import com.zhyq.park.system.entity.SysUserRole;
import com.zhyq.park.system.mapper.SysMenuMapper;
import com.zhyq.park.system.mapper.SysRoleMapper;
import com.zhyq.park.system.mapper.SysRoleMenuMapper;
import com.zhyq.park.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceTest {

    private static final long ADMIN_ROLE_ID = 1L;
    private static final long NORMAL_ROLE_ID = 2L;

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;

    private RoleManagementService service;

    @BeforeEach
    void setUp() {
        service = new RoleManagementService(roleMapper, menuMapper, userRoleMapper, roleMenuMapper);
    }

    @Test
    void normalRolePermissionsCanBeQueried() {
        when(roleMapper.selectById(NORMAL_ROLE_ID)).thenReturn(normalRole());
        when(roleMenuMapper.selectMenuIdsByRoleId(NORMAL_ROLE_ID)).thenReturn(List.of(10L, 11L));

        assertEquals(List.of(10L, 11L), service.getMenuIds(NORMAL_ROLE_ID));
    }

    @Test
    void normalRolePermissionsAreReplacedTransactionally() {
        when(roleMapper.selectById(NORMAL_ROLE_ID)).thenReturn(normalRole());
        when(menuMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(menu(10L, 1), menu(11L, 1)));

        service.saveMenuIds(NORMAL_ROLE_ID, List.of(10L, 11L, 10L));

        verify(roleMenuMapper).delete(any(Wrapper.class));
        verify(roleMenuMapper, times(2)).insert(any(SysRoleMenu.class));
    }

    @Test
    void emptyPermissionsClearNormalRole() {
        when(roleMapper.selectById(NORMAL_ROLE_ID)).thenReturn(normalRole());

        assertDoesNotThrow(() -> service.saveMenuIds(NORMAL_ROLE_ID, List.of()));
        verify(roleMenuMapper).delete(any(Wrapper.class));
        verify(roleMenuMapper, never()).insert(any(SysRoleMenu.class));
    }

    @Test
    void disabledMenuIsRejectedBeforeRolePermissionsChange() {
        when(roleMapper.selectById(NORMAL_ROLE_ID)).thenReturn(normalRole());
        when(menuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(menu(10L, 0)));

        assertThrows(BizException.class,
                () -> service.saveMenuIds(NORMAL_ROLE_ID, List.of(10L)));
        verify(roleMenuMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void protectedAdminRolePermissionsCannotBeChanged() {
        when(roleMapper.selectById(ADMIN_ROLE_ID)).thenReturn(adminRole());

        assertThrows(BizException.class,
                () -> service.saveMenuIds(ADMIN_ROLE_ID, List.of()));
        verify(roleMenuMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void protectedAdminRoleCannotBeUpdated() {
        when(roleMapper.selectById(ADMIN_ROLE_ID)).thenReturn(adminRole());

        assertThrows(BizException.class, () -> service.update(adminRole()));
        verify(roleMapper, never()).updateById(any(SysRole.class));
    }

    @Test
    void protectedAdminRoleCannotBeDeleted() {
        when(roleMapper.selectById(ADMIN_ROLE_ID)).thenReturn(adminRole());

        assertThrows(BizException.class, () -> service.delete(ADMIN_ROLE_ID));
        verify(roleMapper, never()).deleteById(ADMIN_ROLE_ID);
    }

    @Test
    void duplicateRoleCodeIsRejected() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.add(normalRole()));
        verify(roleMapper, never()).insert(any(SysRole.class));
    }

    @Test
    void deletingNormalRoleCleansBothRelationTables() {
        when(roleMapper.selectById(NORMAL_ROLE_ID)).thenReturn(normalRole());

        service.delete(NORMAL_ROLE_ID);

        verify(userRoleMapper).delete(any(Wrapper.class));
        verify(roleMenuMapper).delete(any(Wrapper.class));
        verify(roleMapper).deleteById(NORMAL_ROLE_ID);
    }

    private static SysRole adminRole() {
        return role(ADMIN_ROLE_ID, "admin", "平台超级管理员");
    }

    private static SysRole normalRole() {
        return role(NORMAL_ROLE_ID, "finance", "财务人员");
    }

    private static SysRole role(Long id, String code, String name) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName(name);
        role.setStatus(1);
        return role;
    }

    private static SysMenu menu(Long id, Integer status) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setName("权限" + id);
        menu.setStatus(status);
        return menu;
    }
}
