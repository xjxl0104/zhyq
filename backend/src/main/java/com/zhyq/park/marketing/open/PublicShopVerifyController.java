package com.zhyq.park.marketing.open;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.password.ClientAddressResolver;
import com.zhyq.park.marketing.password.PasswordAttemptLimiter;
import com.zhyq.park.marketing.service.MktConnectorHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 公开 10 秒扫码验店落地与回写接口（支持抖店/淘宝1688/拼多多官方 OAuth 回调及商家单量核验提交）。
 */
@Tag(name = "公开获客-10秒官方扫码验店")
@RestController
@RequestMapping("/open/shop-verify")
@RequiredArgsConstructor
public class PublicShopVerifyController {

    private final MktConnectorHubService connectorHub;
    private final PasswordAttemptLimiter limiter;
    private final ClientAddressResolver addresses;

    @Operation(summary = "读取验店邀请令牌详情")
    @GetMapping("/{token}")
    public Result<Map<String, Object>> detail(@PathVariable String token, HttpServletRequest request) {
        limiter.check("shop-verify-get:" + addresses.resolve(request), 120);
        return Result.ok(connectorHub.getPublicShopVerifySession(token));
    }

    @Operation(summary = "货主提交近30天单量（仅记为货主自报，未经官方核验；72小时内且仅可提交一次）")
    @PostMapping("/{token}/submit")
    public Result<Map<String, Object>> submit(@PathVariable String token,
                                              @RequestBody Map<String, Object> body,
                                              HttpServletRequest request) {
        limiter.check("shop-verify-post:" + addresses.resolve(request), 60);
        Integer orders30d = body.get("orders30d") instanceof Number n ? n.intValue()
                : parseIntSafe(body.get("orders30d"));
        BigDecimal returnRate = parseDecSafe(body.get("returnRatePct"));
        BigDecimal avgWeight = parseDecSafe(body.get("avgWeightKg"));
        String proofNote = body.get("proofNote") == null ? "货主自报近30天单量" : String.valueOf(body.get("proofNote"));
        return Result.ok(connectorHub.submitShopVerifyResult(token, orders30d, returnRate, avgWeight, proofNote));
    }

    private static Integer parseIntSafe(Object o) {
        try { return o == null ? null : Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private static BigDecimal parseDecSafe(Object o) {
        try { return o == null || String.valueOf(o).isBlank() ? null : new BigDecimal(String.valueOf(o).trim()); }
        catch (Exception e) { return null; }
    }
}
