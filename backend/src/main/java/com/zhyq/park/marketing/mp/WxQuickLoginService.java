package com.zhyq.park.marketing.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhyq.park.auth.JwtAccountService;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.password.PasswordAttemptLimiter;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktPromoterService;
import com.zhyq.park.marketing.service.LoginProfileRequirements;
import com.zhyq.park.marketing.wh.WhAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Phone authorization identifies the account; new or incomplete identities also require a name. */
@Service
@RequiredArgsConstructor
public class WxQuickLoginService {
    public enum Identity { MP, WH }
    public record Request(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String jsCode, String appId,
                          Boolean agreed, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String phoneCode,
                          String phone, String inviteCode, String name) {
        public Request(String jsCode, String appId, Boolean agreed, String phoneCode, String phone, String inviteCode) {
            this(jsCode, appId, agreed, phoneCode, phone, inviteCode, null);
        }
        @Override public String toString() { return "QuickLoginRequest[credentials=REDACTED]"; }
    }
    private final MpAuthService mpAuth;
    private final WhAuthService whAuth;
    private final MktPromoterMapper promoters;
    private final MktPromoterService promoterService;
    private final WxPhoneBindingGuard phoneBindingGuard;
    private final MktAuditService audit;
    private final PasswordAttemptLimiter limiter;
    private final JwtService jwt;

    @Transactional
    public Map<String, Object> login(Identity identity, Request request, String remoteAddress) {
        limiter.check("wx-quick:ip:" + remoteAddress, 60);
        if (!Boolean.TRUE.equals(request.agreed())) throw new BizException(400, "请先同意用户协议和隐私政策");
        boolean mock = identity == Identity.MP ? mpAuth.isMockLogin() : whAuth.isMockLogin();
        if (!mock && !StringUtils.hasText(request.appId())) throw new BizException("缺少小程序 AppID");
        if (!mock && StringUtils.hasText(request.phone())) throw new BizException("真实模式必须使用微信手机号授权");
        // Complete both exchanges before any registration. In particular, phone login must be
        // allowed to find an existing business record before an empty profile is created.
        WxSessionClient.Session session = identity == Identity.MP
                ? mpAuth.quickSession(request.jsCode(), request.appId()) : whAuth.quickSession(request.jsCode(), request.appId());
        String phone = identity == Identity.MP ? mpAuth.quickPhone(request.phoneCode(), request.phone())
                : whAuth.quickPhone(request.phoneCode(), request.phone());
        limiter.check("wx-quick:" + identity + ":" + session.openid(), 20);
        try {
            return identity == Identity.MP ? partner(session.openid(), phone, request.inviteCode(), request.name()) : whAuth.quickLoginIdentity(session.openid(), phone, request.name());
        } catch (DuplicateKeyException e) {
            // Propagate out of the transaction: a losing registration must roll back its warehouse,
            // onboarding steps and contact together. A fresh attempt resolves the winning identity.
            throw new BizException(409, "登录资料正在更新，请重新点击登录");
        }
    }

    private Map<String, Object> partner(String openid, String phone, String inviteCode, String name) {
        MktPromoter existing = promoters.selectOne(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getOpenid, openid).last("limit 1"));
        if (existing != null) {
            mpAuth.completeVerifiedProfile(existing, phone, name);
            return partnerResult(existing);
        }
        if (phone != null) {
            // The selected portal owns its identity; the same verified phone may also own a warehouse.
            existing = promoters.selectOne(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getPhone, phone).last("limit 1"));
            if (existing != null) {
                JwtAccountService.assertPromoterActive(existing);
                phoneBindingGuard.assertCanBind("mp", existing.getId());
                if (StringUtils.hasText(existing.getOpenid())) throw new BizException(409, "该手机号已绑定其他微信");
                mpAuth.completeVerifiedProfile(existing, phone, name);
                int changed = promoters.update(null, new LambdaUpdateWrapper<MktPromoter>()
                        .eq(MktPromoter::getId, existing.getId()).isNull(MktPromoter::getOpenid)
                        .in(MktPromoter::getStatus, 1, 3).set(MktPromoter::getOpenid, openid)
                        .set(MktPromoter::getAgreementVersion, "v1").set(MktPromoter::getAgreedAt, LocalDateTime.now()));
                if (changed != 1) throw new BizException(409, "账号状态或微信绑定已变化，请重新登录");
                existing.setOpenid(openid);
                audit.log("promoter.wechat.bind", "promoter", existing.getId(), "微信授权手机号绑定；同意用户协议和隐私政策 v1");
                return partnerResult(existing);
            }
        }
        phone = LoginProfileRequirements.requirePhone(phone);
        name = LoginProfileRequirements.requireName(name);
        String invite = MktPromoterService.requireRegistrationInvite(inviteCode);
        MktPromoter created = new MktPromoter();
        created.setOpenid(openid); created.setPhone(phone); created.setName(name);
        created.setLastLogin(LocalDateTime.now()); created.setAgreementVersion("v1");
        created.setAgreedAt(LocalDateTime.now()); created.setIdVerified(0);
        created.setRemark("微信手机号授权注册");
        promoterService.register(created, invite, "mp");
        return partnerResult(created);
    }

    private Map<String, Object> partnerResult(MktPromoter promoter) {
        JwtAccountService.assertPromoterActive(promoter);
        promoters.update(null, new LambdaUpdateWrapper<MktPromoter>().eq(MktPromoter::getId, promoter.getId())
                .set(MktPromoter::getLastLogin, LocalDateTime.now()));
        Map<String, Object> result = new HashMap<>();
        result.put("registered", true); result.put("token", jwt.issueForIdentity("mp", promoter.getId()));
        result.put("me", MpMeController.profile(promoter));
        return result;
    }

}
