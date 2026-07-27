package com.zhyq.park.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.auth.mapper.AuthQueryMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证:BCrypt 校验密码 + 签发 JWT(无状态)。
 * 角色编码以 ROLE_ 前缀、权限标识原样写入 JWT 的 auth claim。
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
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).last("limit 1"));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(401, "账号不存在或已停用");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(401, "账号或密码错误");
        }

        // 载入角色编码(ROLE_ 前缀)+ 权限标识,写入 JWT
        List<String> authorities = new ArrayList<>();
        for (String code : authQueryMapper.selectRoleCodesByUserId(user.getId())) {
            if (code != null && !code.isBlank()) authorities.add("ROLE_" + code);
        }
        authorities.addAll(authQueryMapper.selectPermsByUserId(user.getId()));

        String token = jwtService.issue(user.getId(), user.getUsername(), authorities);
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
}
