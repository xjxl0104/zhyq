package com.zhyq.park.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.system.entity.SysMenu;
import com.zhyq.park.system.entity.SysRole;
import com.zhyq.park.system.entity.SysRoleMenu;
import com.zhyq.park.system.entity.SysUserRole;
import com.zhyq.park.system.mapper.SysMenuMapper;
import com.zhyq.park.system.mapper.SysRoleMapper;
import com.zhyq.park.system.mapper.SysRoleMenuMapper;
import com.zhyq.park.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private static final String ADMIN_CODE = "admin";

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long add(SysRole role) {
        requireRoleName(role.getName());
        requireUniqueCode(role.getCode(), null);
        role.setCode(role.getCode().trim());
        role.setName(role.getName().trim());
        if (role.getDataScope() == null) {
            role.setDataScope(1);
        }
        if (role.getSort() == null) {
            role.setSort(0);
        }
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        roleMapper.insert(role);
        return role.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysRole role) {
        SysRole existing = requireRole(role.getId());
        rejectProtectedAdmin(existing);
        requireRoleName(role.getName());
        requireUniqueCode(role.getCode(), role.getId());
        role.setCode(role.getCode().trim());
        role.setName(role.getName().trim());
        roleMapper.updateById(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long roleId) {
        SysRole role = requireRole(roleId);
        rejectProtectedAdmin(role);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        roleMapper.deleteById(roleId);
    }

    public List<Long> getMenuIds(Long roleId) {
        requireRole(roleId);
        return roleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMenuIds(Long roleId, List<Long> menuIds) {
        SysRole role = requireRole(roleId);
        rejectProtectedAdmin(role);
        List<Long> normalized = normalizeAndValidateMenus(menuIds);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        normalized.forEach(menuId ->
                roleMenuMapper.insert(new SysRoleMenu(null, roleId, menuId)));
    }

    private SysRole requireRole(Long id) {
        SysRole role = id == null ? null : roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        return role;
    }

    private void rejectProtectedAdmin(SysRole role) {
        if (ADMIN_CODE.equals(role.getCode())) {
            throw new BizException("超级管理员角色受系统保护");
        }
    }

    private void requireRoleName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("请输入角色名称");
        }
    }

    private void requireUniqueCode(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("请输入角色编码");
        }
        LambdaQueryWrapper<SysRole> query = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, code.trim())
                .ne(excludeId != null, SysRole::getId, excludeId);
        Long count = roleMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BizException("角色编码已存在");
        }
    }

    private List<Long> normalizeAndValidateMenus(List<Long> menuIds) {
        List<Long> ids = menuIds == null ? List.of() : menuIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return ids;
        }
        List<SysMenu> menus = menuMapper.selectBatchIds(ids);
        if (menus.size() != ids.size()
                || menus.stream().anyMatch(menu -> !Integer.valueOf(1).equals(menu.getStatus()))) {
            throw new BizException("菜单权限不存在或已停用");
        }
        return ids;
    }
}
