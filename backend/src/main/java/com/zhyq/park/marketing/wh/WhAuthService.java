package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPromoter;
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

import java.util.List;

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

    public LoginResult wxLogin(Long requestedWarehouseId, String jsCode, String clientAppId) {
        if (!mockLogin && StringUtils.hasText(clientAppId) && !clientAppId.equals(appId))
            throw new BizException("当前小程序 AppID 与云仓商家服务配置不一致,请联系管理员检查配置");
        WxSessionClient.Session session = code2Session(jsCode);
        MktWarehouse w = resolveWarehouseByOpenid(session.openid(), requestedWarehouseId);
        if (w == null) return new LoginResult(false, null, session.openid(), null, mockLogin ? null : loginTickets.issue(session));
        touchLogin(w);
        return new LoginResult(true, issue(w), session.openid(), w);
    }

    /** 按 openid 找云仓:优先联系人表(P2-5 多联系人),回落到旧列 contact_openid(过渡期兼容)。 */
    private MktWarehouse resolveWarehouseByOpenid(String openid, Long requestedWarehouseId) {
        if (!StringUtils.hasText(openid)) return null;
        MktWarehouseContact contact = contactMapper.selectOne(new LambdaQueryWrapper<MktWarehouseContact>()
                .eq(MktWarehouseContact::getOpenid, openid).eq(MktWarehouseContact::getStatus, 1).last("limit 1"));
        if (contact != null) {
            if (requestedWarehouseId != null && !requestedWarehouseId.equals(contact.getWarehouseId())) return null;
            return warehouseMapper.selectById(contact.getWarehouseId());
        }
        LambdaQueryWrapper<MktWarehouse> q = new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getContactOpenid, openid).last("limit 1");
        if (requestedWarehouseId != null) q.eq(MktWarehouse::getId, requestedWarehouseId);
        return warehouseMapper.selectOne(q);
    }

    public LoginResult bindPhone(String openid, String phone) {
        if (!mockLogin) throw new BizException("真实模式必须使用微信手机号授权");
        return bindPhoneInternal(openid, phone);
    }

    public LoginResult bindPhoneAuthorized(String openid, String loginTicket, String phoneCode, String encryptedData, String iv) {
        if (mockLogin) throw new BizException("mock 模式请使用明文手机号");
        if (!StringUtils.hasText(phoneCode) && (!StringUtils.hasText(encryptedData) || !StringUtils.hasText(iv)))
            throw new BizException("缺少 phoneCode 或 encryptedData/iv 手机号授权参数");
        String sessionKey = loginTickets.consume(loginTicket, openid);
        String phone = StringUtils.hasText(phoneCode)
                ? wxSessionClient.exchangePhone(appId, appSecret, phoneCode)
                : wxPhoneDecryptor.decryptPhoneNumber(sessionKey, encryptedData, iv, appId);
        return bindPhoneInternal(openid, phone);
    }

    public LoginResult bindPhone(String openid, String encryptedData, String iv) {
        return bindPhoneAuthorized(openid, null, null, encryptedData, iv);
    }

    private LoginResult bindPhoneInternal(String openid, String phone) {
        if (!StringUtils.hasText(openid) || phone == null || !phone.matches("^1\\d{10}$")) throw new BizException("手机号格式不正确");
        // A phone already belonging to a partner is never silently converted into a warehouse identity.
        MktPromoter partner = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getPhone, phone).last("limit 1"));
        if (partner != null) throw new BizException(409, "该手机号已绑定伙伴身份,请使用身份选择");
        MktWarehouse w = warehouseMapper.selectOne(new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getPhone, phone).last("limit 1"));
        if (w == null) return new LoginResult(false, null, openid, null);
        phoneBindingGuard.assertCanBind("wh", w.getId());

        // 该 openid 已作为某云仓联系人 → 幂等返回;已被别的 openid 认领联系人则冲突
        MktWarehouseContact existingContact = contactMapper.selectOne(new LambdaQueryWrapper<MktWarehouseContact>()
                .eq(MktWarehouseContact::getOpenid, openid).last("limit 1"));
        if (existingContact != null) {
            if (!existingContact.getWarehouseId().equals(w.getId())) throw new BizException(409, "该微信已绑定其他云仓");
            return new LoginResult(true, issue(w), openid, w);
        }
        // 旧列兼容:已绑过旧列且是同一 openid → 幂等;绑了别的 openid → 冲突
        if (StringUtils.hasText(w.getContactOpenid()) && !openid.equals(w.getContactOpenid())) throw new BizException(409, "该云仓主联系人已绑定其他微信");

        // 绑定为 member 联系人(多联系人)。openid 唯一键:并发下可能撞唯一键,捕获后按冲突处理。
        MktWarehouseContact c = new MktWarehouseContact();
        c.setWarehouseId(w.getId()); c.setOpenid(openid); c.setPhone(phone);
        c.setName(w.getContact()); c.setRole(StringUtils.hasText(w.getContactOpenid()) ? "member" : "owner");
        c.setStatus(1); c.setProjectId(w.getProjectId());
        try {
            contactMapper.insert(c);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            throw new BizException(409, "该微信已绑定其他云仓");
        }
        // 旧列仍写首次绑定者,过渡期兼容
        if (!StringUtils.hasText(w.getContactOpenid())) {
            warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId, w.getId()).isNull(MktWarehouse::getContactOpenid).set(MktWarehouse::getContactOpenid, openid));
        }
        auditService.log("warehouse.bind", "warehouse", w.getId(), "微信手机号绑定(联系人)");
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
    public boolean isMockLogin() { return mockLogin; }
}
