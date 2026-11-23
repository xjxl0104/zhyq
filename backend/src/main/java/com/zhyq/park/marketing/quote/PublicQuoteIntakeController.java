package com.zhyq.park.marketing.quote;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.password.ClientAddressResolver;
import com.zhyq.park.marketing.password.PasswordAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/open/quote-intake/{token}")
@RequiredArgsConstructor
public class PublicQuoteIntakeController {
    private final QuoteIntakeService intake;
    private final PasswordAttemptLimiter limiter;
    private final ClientAddressResolver addresses;

    @GetMapping
    public Result<Map<String,Object>> form(@PathVariable String token,HttpServletRequest request) {
        limiter.check("quote:view:"+addresses.resolve(request),120);
        return Result.ok(intake.publicStatus(token));
    }
    @PostMapping
    public Result<Map<String,Object>> submit(@PathVariable String token,@RequestBody Map<String,Object> body,HttpServletRequest request) {
        limiter.check("quote:submit:"+addresses.resolve(request),20);
        return Result.ok(intake.submit(token,body));
    }
}
