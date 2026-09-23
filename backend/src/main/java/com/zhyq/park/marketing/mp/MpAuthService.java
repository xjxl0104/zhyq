package com.zhyq.park.marketing.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.service.MktPromoterService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 伙伴小程序登录(PARK-MKT-001 §2.1 / §5.2)。
 *
 * <p>与后台共用 {@link JwtService}(同一密钥),但小程序 token 的 subject = "mp:{promoterId}"、权限只有 ROLE_MP,
 * 所以拿小程序 token 调不了后台接口,后台 token 也过不了 /mp/v1 的 hasRole('MP')。</p>
 *
 * <p>开发期开关 {@code zhyq.mp.mock-login=true}:js_code 直接当 openid 用("mock:" 前缀),不调微信;
 * 拿到 AppID/AppSecret 后填环境变量 {@code WX_MP_APPID / WX_MP_SECRET} 并关掉开关即切真。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MpAuthService {

    public static final String SUBJECT_PREFIX = "mp:";
    public static final String ROLE = "ROLE_MP";
    private static final String MODULE = "marketing";

    private final JwtService jwtService;
    private final MktPromoterMapper promoterMapper;
    private final MktPromoterService promoterService;
    private final BizSettings bizSettings;
    private final WxSessionClient wxSessionClient;
    private final WxPhoneDecryptor wxPhoneDecryptor;
    private final WxPhoneBindingGuard phoneBindingGuard;
    private final WxLoginTickets loginTickets = new WxLoginTickets();

    @Value("${zhyq.mp.mock-login:false}")
    private boolean mockLogin;
    @Value("${zhyq.mp.appid:}")
    private String appId;
    @Value("${zhyq.mp.secret:}")
    private String appSecret;

    public record LoginResult(boolean registered, String token, String openid, MktPromoter promoter, String loginTicket) {
        public LoginResult(boolean registered, String token, String openid, MktPromoter promoter) {
            this(registered, token, openid, promoter, null);
        }
    }

    /** 微信登录:code2Session → 按 openid 找伙伴;找不到返回 registered=false 让前端走手机号授权。 */
    public LoginResult wxLogin(String jsCode) { return wxLogin(jsCode, null); }

    public LoginResult wxLogin(String jsCode, String clientAppId) {
        verifyClientAppId(clientAppId);
        WxSessionClient.Session session = code2Session(jsCode);
        String openid = session.openid();
        MktPromoter p = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getOpenid, openid).last("limit 1"));
        if (p == null) {
            return new LoginResult(false, null, openid, null, mockLogin ? null : loginTickets.issue(session));
        }
        touchLogin(p);
        return new LoginResult(true, issue(p), openid, p);
    }

    /**
     * 手机号授权注册/绑定:openid 未注册 → 用手机号 + 邀请码建伙伴;
     * 手机号已被后台手工录入过(openid 为空)→ 把 openid 绑上去(后台先录、伙伴后来扫码的场景)。
     */
    public LoginResult bindPhone(String openid, String phone, String inviteCode, String name) {
        if (!mockLogin) throw new BizException("真实模式必须使用微信手机号授权");
        return bindPhoneInternal(openid, phone, inviteCode, name);
    }

    public LoginResult bindPhoneAuthorized(String openid, String loginTicket, String phoneCode,
                                           String encryptedData, String iv, String inviteCode, String name) {
        if (mockLogin) throw new BizException("mock 模式请使用明文手机号");
        if (!StringUtils.hasText(phoneCode) && (!StringUtils.hasText(encryptedData) || !StringUtils.hasText(iv)))
            throw new BizException("缺少 phoneCode 或 encryptedData/iv 手机号授权参数");
        String sessionKey = loginTickets.consume(loginTicket, openid);
        String phone = StringUtils.hasText(phoneCode)
                ? wxSessionClient.exchangePhone(appId, appSecret, phoneCode)
                : wxPhoneDecryptor.decryptPhoneNumber(sessionKey, encryptedData, iv, appId);
        if (!StringUtils.hasText(phone) || !phone.matches("^1\\d{10}$")) throw new BizException("手机号格式不正确");
        return bindPhoneInternal(openid, phone, inviteCode, name);
    }

    /** Legacy callers must obtain and send the login ticket before binding. */
    public LoginResult bindPhoneEncrypted(String openid, String encryptedData, String iv, String inviteCode, String name) {
        return bindPhoneAuthorized(openid, null, null, encryptedData, iv, inviteCode, name);
    }

    public LoginResult bindPhone(String openid, String encryptedData, String iv, String inviteCode, String name) {
        return bindPhoneEncrypted(openid, encryptedData, iv, inviteCode, name);
    }

    private LoginResult bindPhoneInternal(String openid, String phone, String inviteCode, String name) {
        if (!StringUtils.hasText(openid)) throw new BizException("请先完成微信登录");
        MktPromoter byOpenid = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getOpenid, openid).last("limit 1"));
        if (byOpenid != null) {
            return new LoginResult(true, issue(byOpenid), openid, byOpenid);
        }
        MktPromoter byPhone = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getPhone, phone).last("limit 1"));
        if (byPhone != null) {
            phoneBindingGuard.assertCanBind("mp", byPhone.getId());
            if (StringUtils.hasText(byPhone.getOpenid())) throw new BizException("该手机号已绑定其他微信");
            int updated = promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>()
                    .eq(MktPromoter::getId, byPhone.getId()).isNull(MktPromoter::getOpenid)
                    .set(MktPromoter::getOpenid, openid).set(MktPromoter::getLastLogin, LocalDateTime.now()));
            if (updated == 0) throw new BizException("该手机号已绑定其他微信");
            byPhone.setOpenid(openid);
            return new LoginResult(true, issue(byPhone), openid, byPhone);
        }
        MktPromoter p = new MktPromoter();
        p.setOpenid(openid);
        p.setPhone(phone);
        p.setName(StringUtils.hasText(name) ? name : "伙伴" + phone.substring(7));
        p.setLastLogin(LocalDateTime.now());
        if (!StringUtils.hasText(inviteCode)) {
            int grace = bizSettings.getInt(MODULE, "invite_grace_days", 7);
            p.setInviteDeadline(LocalDateTime.now().plusDays(grace));
        }
        try {
            promoterService.register(p, inviteCode, "mp");
        } catch (BizException e) {
            MktPromoter existing = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                    .eq(MktPromoter::getPhone, phone).last("limit 1"));
            if (existing != null && !openid.equals(existing.getOpenid())) throw new BizException("该手机号已绑定其他微信");
            throw e;
        }
        return new LoginResult(true, issue(p), openid, p);
    }

    /** 无邀请码注册后,宽限期内补填一次。 */
    public void bindInvite(Long promoterId, String inviteCode) {
        MktPromoter p = promoterMapper.selectById(promoterId);
        if (p == null) throw new BizException("伙伴不存在");
        if (p.getParentId() != null) throw new BizException("已绑定上级,不能再改");
        if (p.getInviteDeadline() == null || p.getInviteDeadline().isBefore(LocalDateTime.now())) {
            throw new BizException("补填邀请码已超过期限");
        }
        promoterService.changeParentByInvite(promoterId, inviteCode);
    }

    /** 当前登录伙伴 id;非小程序 token 抛异常。 */
    public static Long currentPromoterId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName() == null || !a.getName().startsWith(SUBJECT_PREFIX)) {
            throw new BizException(401, "请先登录小程序");
        }
        return Long.valueOf(a.getName().substring(SUBJECT_PREFIX.length()));
    }

    private String issue(MktPromoter p) {
        return jwtService.issue(p.getId(), SUBJECT_PREFIX + p.getId(), List.of(ROLE));
    }

    private void touchLogin(MktPromoter p) {
        promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>()
                .eq(MktPromoter::getId, p.getId()).set(MktPromoter::getLastLogin, LocalDateTime.now()));
    }

    private void verifyClientAppId(String clientAppId) {
        if (!mockLogin && StringUtils.hasText(clientAppId) && !clientAppId.equals(appId))
            throw new BizException("当前小程序 AppID 与园区伙伴服务配置不一致,请联系管理员检查配置");
    }

    /** 微信 jscode2session;mock 模式直接把 code 当 openid。 */
    WxSessionClient.Session code2Session(String jsCode) {
        if (!StringUtils.hasText(jsCode)) throw new BizException("缺少 js_code");
        if (mockLogin) {
            return new WxSessionClient.Session("mock:" + jsCode, null);
        }
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new BizException("小程序 AppID/Secret 未配置");
        }
        try {
            WxSessionClient.Session session = wxSessionClient.exchange(appId, appSecret, jsCode);
            if (session == null || !StringUtils.hasText(session.openid()) || !StringUtils.hasText(session.sessionKey()))
                throw new BizException("微信登录失败: 返回缺少 openid/session_key");
            return session;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[mp] code2Session 失败", e);
            throw new BizException("微信登录失败,请重试");
        }
    }

    /** 供测试:解析 token 看 subject。 */
    Claims parse(String token) {
        return jwtService.parse(token);
    }

    public void setMockLogin(boolean mockLogin) { this.mockLogin = mockLogin; }
    public void setAppId(String appId) { this.appId = appId; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public boolean isMockLogin() { return mockLogin; }
}
