package com.zhyq.park.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.system.dto.SysUserDetailResponse;
import com.zhyq.park.system.dto.SysUserSaveRequest;
import com.zhyq.park.system.dto.UserRoleLabel;
import com.zhyq.park.system.entity.SysRole;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.entity.SysUserMenu;
import com.zhyq.park.system.entity.SysUserRole;
import com.zhyq.park.system.mapper.SysRoleMapper;
import com.zhyq.park.system.mapper.SysUserMapper;
import com.zhyq.park.system.mapper.SysUserMenuMapper;
import com.zhyq.park.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final String ADMIN_CODE = "admin";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMenuMapper userMenuMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserContext currentUserContext;

    @Transactional(rollbackFor = Exception.class)
    public Long create(SysUserSaveRequest request) {
        requireNewUsername(request.getUsername());
        requireNickname(request.getNickname());
        requireNewPassword(request.getPassword());
        List<SysRole> roles = requireEnabledRoles(request.getRoleIds());
        guardAdminRoleChange(false, containsAdmin(roles));

        SysUser user = toNewUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userMapper.insert(user);
        replaceRoles(user.getId(), roles);
        replaceMenus(user.getId(), request.getMenuIds());
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysUserSaveRequest request) {
        SysUser existing = requireUser(request.getId());
        requireNickname(request.getNickname());
        List<SysRole> roles = requireEnabledRoles(request.getRoleIds());
        boolean oldAdmin = userRoleMapper.hasRoleCode(existing.getId(), ADMIN_CODE) > 0;
        boolean newAdmin = containsAdmin(roles);

        guardAdminAccountEdit(oldAdmin);
        guardAdminRoleChange(oldAdmin, newAdmin);
        if (oldAdmin && Integer.valueOf(1).equals(existing.getStatus())
                && (!newAdmin || !Integer.valueOf(1).equals(request.getStatus()))) {
            guardLastActiveAdmin();
        }

        applyEditableFields(existing, request);
        if (StringUtils.hasText(request.getPassword())) {
            if (request.getPassword().length() < 6) {
                throw new BizException("密码至少 6 位");
            }
            existing.setPassword(passwordEncoder.encode(request.getPassword()));
        } else {
            existing.setPassword(null);
        }
        userMapper.updateById(existing);
        replaceRoles(existing.getId(), roles);
        replaceMenus(existing.getId(), request.getMenuIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser existing = requireUser(id);
        if (userRoleMapper.hasRoleCode(id, ADMIN_CODE) > 0) {
            if (!currentUserContext.isAdmin()) {
                throw new BizException("只有超级管理员可以删除超级管理员");
            }
            if (Integer.valueOf(1).equals(existing.getStatus())) {
                guardLastActiveAdmin();
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        userMenuMapper.delete(new LambdaQueryWrapper<SysUserMenu>()
                .eq(SysUserMenu::getUserId, id));
        userMapper.deleteById(id);
    }

    public SysUserDetailResponse get(Long id) {
        SysUser user = requireUser(id);
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(id);
        List<Long> menuIds = userMenuMapper.selectList(
                new LambdaQueryWrapper<SysUserMenu>().eq(SysUserMenu::getUserId, id)
                        .select(SysUserMenu::getMenuId))
                .stream().map(SysUserMenu::getMenuId).toList();
        return new SysUserDetailResponse(user, roleIds, menuIds);
    }

    public void fillRoleNames(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        Map<Long, List<String>> labels = new LinkedHashMap<>();
        for (UserRoleLabel row : userRoleMapper.selectRoleLabelsByUserIds(userIds)) {
            List<String> names = StringUtils.hasText(row.getRoleNames())
                    ? Arrays.stream(row.getRoleNames().split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .toList()
                    : List.of();
            labels.put(row.getUserId(), names);
        }
        users.forEach(user -> user.setRoleNames(labels.getOrDefault(user.getId(), List.of())));
    }

    private void requireNewUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BizException("请输入账号");
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim()));
        if (count != null && count > 0) {
            throw new BizException("账号已存在");
        }
    }

    private void requireNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            throw new BizException("请输入昵称");
        }
    }

    private void requireNewPassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BizException("请设置初始密码");
        }
        if (password.length() < 6) {
            throw new BizException("密码至少 6 位");
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = id == null ? null : userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private List<SysRole> requireEnabledRoles(List<Long> roleIds) {
        List<Long> ids = roleIds == null ? List.of() : roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectBatchIds(ids);
        if (roles.size() != ids.size()
                || roles.stream().anyMatch(role -> !Integer.valueOf(1).equals(role.getStatus()))) {
            throw new BizException("角色不存在或已停用");
        }
        return roles;
    }

    private boolean containsAdmin(List<SysRole> roles) {
        return roles.stream().anyMatch(role -> ADMIN_CODE.equals(role.getCode()));
    }

    private void guardAdminRoleChange(boolean oldAdmin, boolean newAdmin) {
        if (oldAdmin != newAdmin && !currentUserContext.isAdmin()) {
            throw new BizException("只有超级管理员可以授予或撤销超级管理员角色");
        }
    }

    private void guardAdminAccountEdit(boolean targetIsAdmin) {
        if (targetIsAdmin && !currentUserContext.isAdmin()) {
            throw new BizException("只有超级管理员可以编辑超级管理员账号");
        }
    }

    private void guardLastActiveAdmin() {
        if (userRoleMapper.countActiveAdminUsers() <= 1) {
            throw new BizException("系统必须至少保留一个启用的超级管理员");
        }
    }

    private SysUser toNewUser(SysUserSaveRequest request) {
        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        applyEditableFields(user, request);
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getUserType() == null) {
            user.setUserType(1);
        }
        return user;
    }

    private void applyEditableFields(SysUser user, SysUserSaveRequest request) {
        user.setNickname(request.getNickname().trim());
        user.setDeptId(request.getDeptId());
        user.setPostId(request.getPostId());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setGender(request.getGender());
        user.setAvatar(request.getAvatar());
        user.setUserType(request.getUserType());
        user.setStatus(request.getStatus());
        user.setRemark(request.getRemark());
    }

    private void replaceRoles(Long userId, List<SysRole> roles) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        roles.stream()
                .collect(Collectors.toMap(SysRole::getId, role -> role, (left, right) -> left,
                        LinkedHashMap::new))
                .values()
                .forEach(role -> userRoleMapper.insert(new SysUserRole(null, userId, role.getId())));
    }

    private void replaceMenus(Long userId, List<Long> menuIds) {
        userMenuMapper.delete(new LambdaQueryWrapper<SysUserMenu>()
                .eq(SysUserMenu::getUserId, userId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        menuIds.stream().distinct()
                .forEach(menuId -> userMenuMapper.insert(new SysUserMenu(null, userId, menuId)));
    }
}
