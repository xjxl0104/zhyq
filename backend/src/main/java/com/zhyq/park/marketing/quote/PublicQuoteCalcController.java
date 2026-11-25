package com.zhyq.park.marketing.quote;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.password.ClientAddressResolver;
import com.zhyq.park.marketing.password.PasswordAttemptLimiter;
import com.zhyq.park.marketing.service.MktPublicSeaWashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 公开免登录获客钩子：《30 秒云仓代发底价计算器》实时测算与热询盘留资入池接口（带 IP 限流防刷）。
 */
@Tag(name = "公开获客-30秒云仓底价测算器")
@RestController
@RequestMapping("/open/public-calc")
@RequiredArgsConstructor
public class PublicQuoteCalcController {

    private final MktPublicSeaWashService washService;
    private final PasswordAttemptLimiter limiter;
    private final ClientAddressResolver addresses;

    @Operation(summary = "实时测算园区严选云仓一口价区间与年节省额")
    @PostMapping("/estimate")
    public Result<Map<String, Object>> estimate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        limiter.check("public-calc:estimate:" + addresses.resolve(request), 180);
        Long projectId = body.get("projectId") instanceof Number n ? n.longValue() : null;
        String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
        Integer dailyOrders = body.get("dailyOrders") instanceof Number n ? n.intValue() : null;
        BigDecimal avgWeightKg = null;
        if (body.get("avgWeightKg") != null) {
            try { avgWeightKg = new BigDecimal(String.valueOf(body.get("avgWeightKg")).trim()); } catch (Exception ignored) {}
        }
        return Result.ok(washService.estimatePublicQuote(projectId, category, dailyOrders, avgWeightKg));
    }

    @Operation(summary = "授权手机号解锁明细并领试发券(自动过三道防撞闸写入内部公海)")
    @PostMapping("/lead")
    public Result<Map<String, Object>> submitLead(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        limiter.check("public-calc:lead:" + addresses.resolve(request), 15);
        return Result.ok(washService.submitPublicCalcLead(body));
    }
}
