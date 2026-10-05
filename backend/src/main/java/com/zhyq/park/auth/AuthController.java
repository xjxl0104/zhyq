package com.zhyq.park.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.auth.mapper.AuthQueryMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import com.zhyq.park.system.entity.SysMenu;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证:BCrypt 校验密码 + 签发 JWT(无状态)。
 * 签名令牌包含显式主体类型；每次请求重新校验当前账号状态及权限。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper userMapper;
    private final AuthQueryMapper authQueryMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BizException(401, "请输入账号密码");
        }
        if (JwtAccountService.reservedUsername(username)) throw new BizException(401, "账号或密码错误");
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).last("limit 1"));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(401, "账号不存在或已停用");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(401, "账号或密码错误");
        }

        String token = jwtService.issueForIdentity("admin", user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("expiresIn", jwtService.getExpireSeconds());
        return Result.ok(data);
    }

    @Operation(summary = "登出(无状态:前端删除本地 token 即可)")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT 无状态,服务端不持有 token;前端清除本地 token 完成登出
        return Result.ok();
    }

    @Operation(summary = "获取当前用户可见菜单 ID(角色菜单 ∪ 直接菜单)")
    @GetMapping("/my-menu-ids")
    public Result<List<Long>> myMenuIds() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).last("limit 1"));
        if (user == null) {
            return Result.ok(List.of());
        }
        // admin 角色拥有全部菜单
        List<String> roleCodes = authQueryMapper.selectRoleCodesByUserId(user.getId());
        if (roleCodes.contains("admin")) {
            return Result.ok(List.of(-1L));
        }
        return Result.ok(authQueryMapper.selectGrantedMenusByUserId(user.getId()).stream()
                .map(SysMenu::getId).toList());
    }

    public record GrantedMenu(Long id, Long parentId, String name, Integer type,
                              String path, String perm, Integer status) {
        static GrantedMenu of(SysMenu menu) {
            return new GrantedMenu(menu.getId(), menu.getParentId(), menu.getName(),
                    menu.getType(), menu.getPath(), menu.getPerm(), menu.getStatus());
        }
    }
    public record MyAccess(boolean admin, List<GrantedMenu> menus) {}

    @Operation(summary = "当前用户的有效导航与操作权限")
    @GetMapping("/my-access")
    public Result<MyAccess> myAccess() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, auth.getName()).last("limit 1"));
        if (user == null) throw new BizException(401, "登录状态已失效");
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_admin".equals(authority.getAuthority()));
        return Result.ok(new MyAccess(admin, admin ? List.of()
                : authQueryMapper.selectGrantedMenusByUserId(user.getId()).stream()
                        .map(GrantedMenu::of).toList()));
    }
}
