package com.zhyq.park.marketing.password;

import com.zhyq.park.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.zhyq.park.marketing.password.PasswordAuthService.Identity.MP;
import static com.zhyq.park.marketing.password.PasswordAuthService.Identity.WH;

@RestController
@RequiredArgsConstructor
public class PasswordAuthController {
    private final PasswordAuthService service;
    private final ClientAddressResolver clientAddresses;

    @PostMapping("/mp/v1/auth/password-login")
    public Result<Map<String, Object>> mpLogin(@RequestBody PasswordAuthService.LoginRequest body, HttpServletRequest request) {
        return Result.ok(service.login(MP, body, clientAddresses.resolve(request)));
    }

    @PostMapping("/wh/v1/auth/password-login")
    public Result<Map<String, Object>> whLogin(@RequestBody PasswordAuthService.LoginRequest body, HttpServletRequest request) {
        return Result.ok(service.login(WH, body, clientAddresses.resolve(request)));
    }

    @PostMapping("/mp/v1/auth/password-register")
    public Result<Map<String, Object>> mpRegister(@RequestBody PasswordAuthService.RegisterRequest body, HttpServletRequest request) {
        return Result.ok(service.register(MP, body, clientAddresses.resolve(request)));
    }

    @PostMapping("/wh/v1/auth/password-register")
    public Result<Map<String, Object>> whRegister(@RequestBody PasswordAuthService.RegisterRequest body, HttpServletRequest request) {
        return Result.ok(service.register(WH, body, clientAddresses.resolve(request)));
    }

    @GetMapping("/mp/v1/auth/password-status")
    @PreAuthorize("hasRole('MP')")
    public Result<Map<String, Object>> mpStatus() { return Result.ok(service.status(MP)); }

    @GetMapping("/wh/v1/auth/password-status")
    @PreAuthorize("hasRole('WH')")
    public Result<Map<String, Object>> whStatus() { return Result.ok(service.status(WH)); }

    @PostMapping("/mp/v1/auth/password-setup")
    @PreAuthorize("hasRole('MP')")
    public Result<Void> mpSetup(@RequestBody PasswordAuthService.SetupRequest body, HttpServletRequest request) {
        service.setup(MP, body, clientAddresses.resolve(request));
        return Result.ok();
    }

    @PostMapping("/wh/v1/auth/password-setup")
    @PreAuthorize("hasRole('WH')")
    public Result<Void> whSetup(@RequestBody PasswordAuthService.SetupRequest body, HttpServletRequest request) {
        service.setup(WH, body, clientAddresses.resolve(request));
        return Result.ok();
    }
}
