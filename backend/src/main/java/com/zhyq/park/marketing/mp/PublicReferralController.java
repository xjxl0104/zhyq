package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.password.ClientAddressResolver;
import com.zhyq.park.marketing.password.PasswordAttemptLimiter;
import com.zhyq.park.marketing.service.MktReferralLinkService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/mp/v1/public/referral")
@RequiredArgsConstructor
public class PublicReferralController {
    private final MktReferralLinkService links;
    private final PasswordAttemptLimiter limiter;
    private final ClientAddressResolver addresses;
    @GetMapping("/{token}")
    public Result<Map<String,Object>> form(@PathVariable String token, HttpServletRequest request) {
        limiter.check("referral:view:" + addresses.resolve(request), 120);
        return Result.ok(links.form(token));
    }
    @PostMapping("/{token}")
    public Result<Map<String,Object>> submit(@PathVariable String token, @RequestBody Map<String,Object> body, HttpServletRequest request) {
        limiter.check("referral:submit:" + addresses.resolve(request), 20);
        return Result.ok(links.submit(token, body));
    }
}
