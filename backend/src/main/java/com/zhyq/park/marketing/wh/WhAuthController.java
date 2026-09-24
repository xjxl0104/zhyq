package com.zhyq.park.marketing.wh;

import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "云仓端-认证")
@RestController
@RequestMapping("/wh/v1/auth")
@RequiredArgsConstructor
public class WhAuthController {
    private final WhAuthService authService;

    @Operation(summary = "微信登录")
    @PostMapping("/wx-login")
    public Result<WhAuthService.LoginResult> wxLogin(@RequestBody Map<String, Object> body) {
        Object id = body.get("warehouseId");
        Long warehouseId = id == null ? null : Long.valueOf(id.toString());
        return Result.ok(authService.wxLogin(warehouseId, text(body, "jsCode", "js_code"), text(body, "appId")));
    }

    @Operation(summary = "绑定手机号")
    @PostMapping("/bind-phone")
    public Result<WhAuthService.LoginResult> bindPhone(@RequestBody Map<String, Object> body) {
        String openid = text(body, "openid");
        String encrypted = text(body, "encryptedData", "encrypted_data");
        String iv = text(body, "iv");
        if (!authService.isMockLogin()) return Result.ok(authService.bindPhoneAuthorized(openid,
                text(body, "loginTicket"), text(body, "phoneCode"), encrypted, iv));
        return Result.ok(authService.bindPhone(openid, text(body, "phone")));
    }

    private static String text(Map<String, Object> body, String... keys) {
        for (String key : keys) if (body.get(key) != null) return body.get(key).toString().trim();
        return null;
    }
}
