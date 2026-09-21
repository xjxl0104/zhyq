package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mp.WxPhoneDecryptor;
import com.zhyq.park.marketing.mp.WxSessionClient;
import com.zhyq.park.marketing.service.MktAuditService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<String, SessionKey> sessionKeys = new ConcurrentHashMap<>();
    private record SessionKey(String value, long expiresAt) {}
    @Value("${zhyq.mp.mock-login:true}") private boolean mockLogin;
    @Value("${zhyq.mp.appid:}") private String appId;
    @Value("${zhyq.mp.secret:}") private String appSecret;

    public WhAuthService(JwtService jwtService, MktWarehouseMapper warehouseMapper,
                         MktPromoterMapper promoterMapper, MktAuditService auditService,
                         WxSessionClient wxSessionClient, WxPhoneDecryptor wxPhoneDecryptor) {
        this.jwtService = jwtService; this.warehouseMapper = warehouseMapper; this.promoterMapper = promoterMapper;
        this.auditService = auditService; this.wxSessionClient = wxSessionClient; this.wxPhoneDecryptor = wxPhoneDecryptor;
    }

    public record LoginResult(boolean registered, String token, String openid, MktWarehouse warehouse) {
        public Long warehouseId() { return warehouse == null ? null : warehouse.getId(); }
    }

    public LoginResult wxLogin(String jsCode) { return wxLogin(null, jsCode); }

    /** Optional warehouse id is only a lookup hint; ownership still requires contact_openid/phone match. */
    public LoginResult wxLogin(Long requestedWarehouseId, String jsCode) {
        WxSessionClient.Session session = code2Session(jsCode);
        if (!mockLogin) sessionKeys.put(session.openid(), new SessionKey(session.sessionKey(), System.currentTimeMillis() + 300_000));
        LambdaQueryWrapper<MktWarehouse> q = new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getContactOpenid, session.openid()).last("limit 1");
        if (requestedWarehouseId != null) q.eq(MktWarehouse::getId, requestedWarehouseId);
        MktWarehouse w = warehouseMapper.selectOne(q);
        if (w == null) return new LoginResult(false, null, session.openid(), null);
        touchLogin(w);
        return new LoginResult(true, issue(w), session.openid(), w);
    }

    public LoginResult bindPhone(String openid, String phone) {
        if (!mockLogin) throw new BizException("真实模式必须使用 encryptedData 和 iv");
        return bindPhoneInternal(openid, phone);
    }

    public LoginResult bindPhone(String openid, String encryptedData, String iv) {
        if (mockLogin) throw new BizException("mock 模式请使用明文手机号");
        if (!StringUtils.hasText(openid) || !StringUtils.hasText(encryptedData) || !StringUtils.hasText(iv)) throw new BizException("缺少微信登录参数");
        SessionKey key = sessionKeys.remove(openid);
        if (key == null || key.expiresAt() < System.currentTimeMillis()) throw new BizException("请先完成微信登录");
        String phone = wxPhoneDecryptor.decryptPhoneNumber(key.value(), encryptedData, iv);
        return bindPhoneInternal(openid, phone);
    }

    private LoginResult bindPhoneInternal(String openid, String phone) {
        if (!StringUtils.hasText(openid) || phone == null || !phone.matches("^1\\d{10}$")) throw new BizException("手机号格式不正确");
        // A phone already belonging to a partner is never silently converted into a warehouse identity.
        MktPromoter partner = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getPhone, phone).last("limit 1"));
        if (partner != null) throw new BizException(409, "该手机号已绑定伙伴身份,请使用身份选择");
        MktWarehouse w = warehouseMapper.selectOne(new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getPhone, phone).last("limit 1"));
        if (w == null) return new LoginResult(false, null, openid, null);
        if (StringUtils.hasText(w.getContactOpenid()) && !openid.equals(w.getContactOpenid())) throw new BizException(409, "该云仓已绑定其他微信");
        int updated = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId, w.getId()).isNull(MktWarehouse::getContactOpenid).set(MktWarehouse::getContactOpenid, openid));
        if (updated == 0 && !openid.equals(w.getContactOpenid())) throw new BizException(409, "该云仓已绑定其他微信");
        w.setContactOpenid(openid);
        auditService.log("warehouse.bind", "warehouse", w.getId(), "微信手机号绑定");
        return new LoginResult(true, issue(w), openid, w);
    }

    private void touchLogin(MktWarehouse w) { /* contact_openid is the only persisted identity marker in this schema */ }

    private String issue(MktWarehouse w) { return jwtService.issue(w.getId(), SUBJECT_PREFIX + w.getId(), List.of(ROLE)); }

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
    public void setMockLogin(boolean value) { mockLogin = value; }
    public void setAppId(String value) { appId = value; }
    public void setAppSecret(String value) { appSecret = value; }
}
