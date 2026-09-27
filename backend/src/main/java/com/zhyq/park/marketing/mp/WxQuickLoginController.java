package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.password.ClientAddressResolver;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WxQuickLoginController {
    private final WxQuickLoginService service;
    private final ClientAddressResolver clientAddresses;

    @Operation(summary = "伙伴微信快捷登录；手机号授权可选，资料可后补")
    @PostMapping("/mp/v1/auth/quick-login")
    public Result<Map<String, Object>> partner(@RequestBody WxQuickLoginService.Request body, HttpServletRequest request) {
        return Result.ok(service.login(WxQuickLoginService.Identity.MP, body, clientAddresses.resolve(request)));
    }

    @Operation(summary = "云仓微信快捷登录；新用户进入待审工作台")
    @PostMapping("/wh/v1/auth/quick-login")
    public Result<Map<String, Object>> warehouse(@RequestBody WxQuickLoginService.Request body, HttpServletRequest request) {
        return Result.ok(service.login(WxQuickLoginService.Identity.WH, body, clientAddresses.resolve(request)));
    }
}
