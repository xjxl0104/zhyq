package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktPromoter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** 伙伴小程序登录(permitAll,见 SecurityConfig)。 */
@Tag(name = "小程序-登录")
@RestController
@RequestMapping("/mp/v1/auth")
@RequiredArgsConstructor
public class MpAuthController {

    private final MpAuthService authService;

    @Operation(summary = "微信登录:js_code → 已注册返 token,未注册返 registered=false + openid")
    @PostMapping("/wx-login")
    public Result<Map<String, Object>> wxLogin(@RequestBody Map<String, String> body) {
        MpAuthService.LoginResult r = authService.wxLogin(body.get("jsCode"));
        return Result.ok(toMap(r));
    }

    @Operation(summary = "手机号授权注册/绑定(阶段 B 用明文手机号;接真微信后改 encryptedData 解密)")
    @PostMapping("/bind-phone")
    public Result<Map<String, Object>> bindPhone(@RequestBody Map<String, String> body) {
        MpAuthService.LoginResult r;
        if (authService.isMockLogin()) {
            String phone = body.get("phone");
            if (phone == null || !phone.matches("^1\\d{10}$")) throw new BizException("手机号格式不正确");
            r = authService.bindPhone(body.get("openid"), phone, body.get("inviteCode"), body.get("name"));
        } else {
            if (body.get("encryptedData") == null || body.get("iv") == null)
                throw new BizException("缺少 encryptedData 或 iv");
            r = authService.bindPhoneEncrypted(body.get("openid"), body.get("encryptedData"), body.get("iv"),
                    body.get("inviteCode"), body.get("name"));
        }
        return Result.ok(toMap(r));
    }

    void setMockLogin(boolean mockLogin) { authService.setMockLogin(mockLogin); }

    private static Map<String, Object> toMap(MpAuthService.LoginResult r) {
        Map<String, Object> m = new HashMap<>();
        m.put("registered", r.registered());
        m.put("token", r.token());
        m.put("openid", r.openid());
        if (r.promoter() != null) m.put("me", MpMeController.profile(r.promoter()));
        return m;
    }
}
