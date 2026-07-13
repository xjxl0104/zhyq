package com.zhyq.park.auth;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 最简登录(演示级):
 * 密码 = SHA-256(明文 + 盐"zhyq");登录成功签发内存 token,重启即失效。
 * 正式上线应替换为 Spring Security + BCrypt + JWT。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper userMapper;
    private final TokenStore tokenStore;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            throw new BizException(401, "请输入账号密码");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).last("limit 1"));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(401, "账号不存在或已停用");
        }
        String hash = DigestUtil.sha256Hex(password + "zhyq");
        if (!hash.equalsIgnoreCase(user.getPassword())) {
            throw new BizException(401, "账号或密码错误");
        }
        String token = tokenStore.issue(user.getUsername());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        return Result.ok(data);
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            tokenStore.revoke(auth.substring(7));
        }
        return Result.ok();
    }
}
