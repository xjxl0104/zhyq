package com.zhyq.park.marketing.quote;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.mp.DefaultWxSessionClient;
import com.zhyq.park.marketing.mp.MpAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/mp/v1/quote/customers/{id}")
@PreAuthorize("hasRole('MP')")
@RequiredArgsConstructor
public class PartnerQuoteIntakeController {
    private final QuoteIntakeService intake;
    private final DefaultWxSessionClient wechat;
    @Value("${zhyq.mp.appid:}") private String appId;
    @Value("${zhyq.mp.secret:}") private String appSecret;

    @GetMapping
    public Result<Map<String,Object>> state(@PathVariable long id) {
        return Result.ok(intake.partner(MpAuthService.currentPromoterId(),id));
    }
    @PostMapping("/intent")
    public Result<Map<String,Object>> markIntent(@PathVariable long id) {
        return Result.ok(intake.markIntent(MpAuthService.currentPromoterId(),id));
    }
    @GetMapping("/code")
    public ResponseEntity<byte[]> code(@PathVariable long id,@RequestParam(defaultValue="release") String env) {
        String token=intake.partnerToken(MpAuthService.currentPromoterId(),id);
        if(appId.isBlank() || appSecret.isBlank()) throw new BizException("小程序码尚未配置，请分享填报卡片");
        byte[] image=wechat.customerQuoteCode(appId,appSecret,token,env);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(image[0]==(byte)137?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG).body(image);
    }
}
