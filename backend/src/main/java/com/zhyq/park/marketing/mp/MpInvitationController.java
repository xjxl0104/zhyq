package com.zhyq.park.marketing.mp;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
@RestController
@RequestMapping("/mp/v1/poster")
@PreAuthorize("hasRole('MP')")
@RequiredArgsConstructor
public class MpInvitationController {
    private final MktPromoterMapper promoters;
    private final DefaultWxSessionClient wechat;
    @Value("${zhyq.mp.appid:}") private String appId;
    @Value("${zhyq.mp.secret:}") private String appSecret;
    private final Map<String, CachedImage> cache = new ConcurrentHashMap<>();
    private record CachedImage(byte[] bytes,long expires) {}
    @GetMapping("/code")
    public ResponseEntity<byte[]> code(@RequestParam(defaultValue="release") String env) {
        var p = promoters.selectById(MpAuthService.currentPromoterId());
        if (p == null || !Integer.valueOf(1).equals(p.getStatus())) throw new BizException("伙伴状态异常");
        if (appId.isBlank() || appSecret.isBlank()) throw new BizException("微信服务尚未配置，可复制邀请码邀请");
        String key = p.getInviteCode() + ":" + env;
        CachedImage image = cache.get(key);
        if (image == null || image.expires() < System.currentTimeMillis()) {
            image = new CachedImage(wechat.invitationCode(appId, appSecret, p.getInviteCode(), env),System.currentTimeMillis()+3600_000);
            if (cache.size() >= 1024) cache.clear();
            cache.put(key,image);
        }
        byte[] bytes = image.bytes();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(bytes[0] == (byte)137 ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG).body(bytes);
    }
}
