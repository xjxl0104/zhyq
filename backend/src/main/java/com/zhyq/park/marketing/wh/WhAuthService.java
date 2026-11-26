package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.auth.JwtAccountService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseContactMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mp.WxPhoneDecryptor;
import com.zhyq.park.marketing.mp.WxSessionClient;
import com.zhyq.park.marketing.mp.WxLoginTickets;
import com.zhyq.park.marketing.mp.WxPhoneBindingGuard;
import com.zhyq.park.marketing.service.MktAuditService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


/** 微信身份 for warehouse portal. One contact_openid owns one warehouse in this phase. */
@Slf4j
@Service
public class WhAuthService {
    public static final String SUBJECT_PREFIX = "wh:";
    public static final String ROLE = "ROLE_WH";
    private final JwtService jwtService;
    private final MktWarehouseMapper warehouseMapper;
    private final MktPromoterMapper promoterMapper;
    private final MktAuditService auditService;
    private final WxSessionClient wxSessionClient;
    private final WxPhoneDecryptor wxPhoneDecryptor;
    private final MktWarehouseContactMapper contactMapper;
    private final WxPhoneBindingGuard phoneBindingGuard;
    private WhIdentityService identityService;
    @org.springframework.beans.factory.annotation.Autowired
    public void setIdentityService(WhIdentityService value) { this.identityService = value; }
    public java.util.Map<String, Object> quickLoginIdentity(String openid, String phone, String name) { return identityService.quickLoginIdentity(openid, phone, name); }
    public java.util.Map<String,Object> memberLogin(String openid, String phone, String name) { return identityService.memberLogin(openid, phone, name); }
    public String issueOwnerToken(Long warehouseId) { return identityService.issueOwnerToken(warehouseId); }
    public boolean ownerPasswordResetRequired(Long warehouseId) { return identityService.ownerPasswordResetRequired(warehouseId); }
    public boolean beginOwnerPasswordSetup(Long warehouseId) { return identityService.beginOwnerPasswordSetup(warehouseId); }
    public void finishOwnerPasswordSetup(Long warehouseId) { identityService.finishOwnerPasswordSetup(warehouseId); }
    private final WxLoginTickets loginTickets = new WxLoginTickets();
    // 按身份显式配置凭据;共用同一小程序时配置同一组 AppID/Secret,不自动跨端回落
    @Value("${zhyq.wh.mock-login:false}") private boolean mockLogin;
    @Value("${zhyq.wh.appid:}") private String appId;
    @Value("${zhyq.wh.secret:}") private String appSecret;

    public WhAuthService(JwtService jwtService, MktWarehouseMapper warehouseMapper,
                         MktPromoterMapper promoterMapper, MktAuditService auditService,
                         WxSessionClient wxSessionClient, WxPhoneDecryptor wxPhoneDecryptor,
                         MktWarehouseContactMapper contactMapper, WxPhoneBindingGuard phoneBindingGuard) {
        this.jwtService = jwtService; this.warehouseMapper = warehouseMapper; this.promoterMapper = promoterMapper;
        this.auditService = auditService; this.wxSessionClient = wxSessionClient; this.wxPhoneDecryptor = wxPhoneDecryptor;
        this.contactMapper = contactMapper;
        this.phoneBindingGuard = phoneBindingGuard;
    }

    public record LoginResult(boolean registered, String token, String openid, MktWarehouse warehouse, String loginTicket) {
        public LoginResult(boolean registered, String token, String openid, MktWarehouse warehouse) {
            this(registered, token, openid, warehouse, null);
        }
        public Long warehouseId() { return warehouse == null ? null : warehouse.getId(); }
    }

    public LoginResult wxLogin(String jsCode) { return wxLogin(null, jsCode); }

    /** Optional warehouse id is only a lookup hint; ownership still requires a contact row (or legacy contact_openid) match. */
    public LoginResult wxLogin(Long requestedWarehouseId, String jsCode) { return wxLogin(requestedWarehouseId, jsCode, null); }

    @org.springframework.transaction.annotation.Transactional
    public LoginResult wxLogin(Long requestedWarehouseId, String jsCode, String clientAppId) {
        if (!mockLogin && StringUtils.hasText(clientAppId) && !clientAppId.equals(appId))
            throw new BizException("当前小程序 AppID 与云仓商家服务配置不一致,请联系管理员检查配置");
        WxSessionClient.Session session = code2Session(jsCode);
        WhIdentityService.Identity identity = identityService.findByOpenid(session.openid(), requestedWarehouseId);
        if (identity == null) return new LoginResult(false, null, session.openid(), null, mockLogin ? null : loginTickets.issue(session));
        return identityResult(identity, session.openid());
    }

    public LoginResult bindPhone(String openid, String phone) {
        return bindPhoneWithName(openid, phone, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public LoginResult bindPhoneWithName(String openid, String phone, String name) {
        if (!mockLogin) throw new BizException("真实模式必须使用微信手机号授权");
        return bindPhoneInternal(openid, phone, name);
    }

    public LoginResult bindPhoneAuthorized(String openid, String loginTicket, String phoneCode, String encryptedData, String iv) {
        return bindPhoneAuthorized(openid, loginTicket, phoneCode, encryptedData, iv, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public LoginResult bindPhoneAuthorized(String openid, String loginTicket, String phoneCode, String encryptedData, String iv, String name) {
        if (mockLogin) throw new BizException("mock 模式请使用明文手机号");
        if (!StringUtils.hasText(phoneCode) && (!StringUtils.hasText(encryptedData) || !StringUtils.hasText(iv)))
            throw new BizException("缺少 phoneCode 或 encryptedData/iv 手机号授权参数");
        String sessionKey = loginTickets.consume(loginTicket, openid);
        String phone = StringUtils.hasText(phoneCode)
                ? wxSessionClient.exchangePhone(appId, appSecret, phoneCode)
                : wxPhoneDecryptor.decryptPhoneNumber(sessionKey, encryptedData, iv, appId);
        return bindPhoneInternal(openid, phone, name);
    }

    public LoginResult bindPhone(String openid, String encryptedData, String iv) {
        return bindPhoneAuthorized(openid, null, null, encryptedData, iv);
    }

    private LoginResult bindPhoneInternal(String openid, String phone, String name) {
        WhIdentityService.Identity identity = identityService.bindVerifiedPhone(openid, phone, name);
        return identity == null ? new LoginResult(false, null, openid, null) : identityResult(identity, openid);
    }

    private LoginResult identityResult(WhIdentityService.Identity identity, String openid) {
        return new LoginResult(true, identityService.issueToken(identity.contact()), openid, WhIdentityService.safeWarehouse(identity.warehouse()));
    }

    WxSessionClient.Session code2Session(String jsCode) {
        if (!StringUtils.hasText(jsCode)) throw new BizException("缺少 js_code");
        if (mockLogin) return new WxSessionClient.Session("mock:" + jsCode, null);
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) throw new BizException("小程序 AppID/Secret 未配置");
        try {
            WxSessionClient.Session session = wxSessionClient.exchange(appId, appSecret, jsCode);
            if (session == null || !StringUtils.hasText(session.openid()) || !StringUtils.hasText(session.sessionKey())) {
                throw new BizException("微信登录失败: 返回缺少 openid/session_key");
            }
            return session;
        }
        catch (BizException e) { throw e; }
        catch (Exception e) { log.error("[wh] code2Session 失败", e); throw new BizException("微信登录失败,请重试"); }
    }

    public Claims parse(String token) { return jwtService.parse(token); }
    /** Shortcut login accepts only a server-exchanged WeChat identity; no client openid/session key. */
    public WxSessionClient.Session quickSession(String jsCode, String clientAppId) {
        if (!mockLogin && !StringUtils.hasText(clientAppId)) throw new BizException("缺少小程序 AppID");
        if (!mockLogin && !clientAppId.equals(appId)) throw new BizException("当前小程序 AppID 与服务配置不一致");
        return code2Session(jsCode);
    }

    public String quickPhone(String phoneCode, String plaintextPhone) {
        if (!mockLogin && StringUtils.hasText(plaintextPhone)) throw new BizException("真实模式必须使用微信手机号授权");
        if (!StringUtils.hasText(phoneCode) && !StringUtils.hasText(plaintextPhone)) return null;
        String phone;
        if (mockLogin) {
            phone = plaintextPhone;
        } else {
            phone = wxSessionClient.exchangePhone(appId, appSecret, phoneCode);
        }
        if (!StringUtils.hasText(phone) || !phone.matches("^1\\d{10}$")) throw new BizException("手机号授权失败，请重新授权");
        return phone;
    }

    public void setMockLogin(boolean value) { mockLogin = value; }
    public void setAppId(String value) { appId = value; }
    public void setAppSecret(String value) { appSecret = value; }
    public boolean isMockLogin() { return mockLogin; }
}
