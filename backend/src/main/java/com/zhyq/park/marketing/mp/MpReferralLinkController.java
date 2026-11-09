package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.service.MktReferralLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/mp/v1/referral-link")
@PreAuthorize("hasRole('MP')")
@RequiredArgsConstructor
public class MpReferralLinkController {
    private final MktReferralLinkService links;
    private final DefaultWxSessionClient wechat;
    @Value("${zhyq.mp.appid:}") private String appId;
    @Value("${zhyq.mp.secret:}") private String appSecret;
    private final Map<String,Cached> cache = new ConcurrentHashMap<>();
    private record Cached(byte[] bytes, long expires) {}
    @GetMapping
    public Result<Map<String,Object>> link() { return Result.ok(links.link(MpAuthService.currentPromoterId())); }
    @GetMapping("/code")
    public ResponseEntity<byte[]> code(@RequestParam(defaultValue="release") String env) {
        if (!java.util.Set.of("release", "trial", "develop").contains(env)) throw new BizException("小程序版本不正确");
        String token = links.link(MpAuthService.currentPromoterId()).get("token").toString();
        if (appId.isBlank() || appSecret.isBlank()) throw new BizException("微信服务尚未配置，请联系运营");
        String key = token + ":" + env;
        Cached item = cache.get(key);
        if (item == null || item.expires() <= System.currentTimeMillis()) {
            item = new Cached(wechat.customerReferralCode(appId, appSecret, token, env), System.currentTimeMillis() + 3600000);
            if (cache.size() >= 1024) cache.clear();
            cache.put(key, item);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(item.bytes()[0] == (byte)137 ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG).body(item.bytes());
    }
}
