package com.zhyq.park.marketing.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhyq.park.auth.JwtAccountService;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseContactMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.password.PasswordAttemptLimiter;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktPromoterService;
import com.zhyq.park.marketing.service.MktSelfProfileService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import com.zhyq.park.marketing.wh.WhAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** One server-verified login request can enter the workspace before filling a business profile. */
@Service
@RequiredArgsConstructor
public class WxQuickLoginService {
    public enum Identity { MP, WH }
    public record Request(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String jsCode, String appId,
                          Boolean agreed, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String phoneCode,
                          String phone, String inviteCode) {
        @Override public String toString() { return "QuickLoginRequest[credentials=REDACTED]"; }
    }
    private final MpAuthService mpAuth;
    private final WhAuthService whAuth;
    private final MktPromoterMapper promoters;
    private final MktWarehouseMapper warehouses;
    private final MktWarehouseContactMapper contacts;
    private final MktPromoterService promoterService;
    private final MktWarehouseOnboardingService onboarding;
    private final WxPhoneBindingGuard phoneBindingGuard;
    private final MktAuditService audit;
    private final BizSettings settings;
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
            return identity == Identity.MP ? partner(session.openid(), phone, request.inviteCode()) : warehouse(session.openid(), phone);
        } catch (DuplicateKeyException e) {
            // Propagate out of the transaction: a losing registration must roll back its warehouse,
            // onboarding steps and contact together. A fresh attempt resolves the winning identity.
            throw new BizException(409, "登录资料正在更新，请重新点击登录");
        }
    }

    private Map<String, Object> partner(String openid, String phone, String inviteCode) {
        MktPromoter existing = promoters.selectOne(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getOpenid, openid).last("limit 1"));
        if (existing != null) return partnerResult(existing);
        if (phone != null) {
            if (warehouses.selectCount(new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getPhone, phone)) > 0
                    || contacts.selectCount(new LambdaQueryWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getPhone, phone)) > 0)
                throw new BizException(409, "该手机号已绑定云仓身份，请选择云仓身份登录");
            existing = promoters.selectOne(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getPhone, phone).last("limit 1"));
            if (existing != null) {
                JwtAccountService.assertPromoterActive(existing);
                phoneBindingGuard.assertCanBind("mp", existing.getId());
                if (StringUtils.hasText(existing.getOpenid())) throw new BizException(409, "该手机号已绑定其他微信");
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
        String invite = inviteCode == null ? null : inviteCode.trim().toUpperCase(Locale.ROOT);
        if (StringUtils.hasText(invite) && !invite.matches("^[A-Z2-9]{8}$")) throw new BizException("邀请码格式不正确");
        MktPromoter created = new MktPromoter();
        created.setOpenid(openid); created.setPhone(phone); created.setName("园区伙伴");
        created.setLastLogin(LocalDateTime.now()); created.setAgreementVersion("v1");
        created.setAgreedAt(LocalDateTime.now()); created.setIdVerified(0);
        created.setRemark(phone == null ? "微信快捷注册；联系资料待补充" : "微信手机号授权注册");
        if (!StringUtils.hasText(invite))
            created.setInviteDeadline(LocalDateTime.now().plusDays(settings.getInt("marketing", "invite_grace_days", 7)));
        if (phone == null) promoterService.registerWithoutPhone(created, invite);
        else promoterService.register(created, invite, "mp");
        return partnerResult(created);
    }

    private Map<String, Object> warehouse(String openid, String phone) {
        // Query inactive contacts too: never fall through to the legacy owner column or create a
        // second warehouse after an administrator disables a contact.
        MktWarehouseContact contact = contacts.selectOne(new LambdaQueryWrapper<MktWarehouseContact>()
                .eq(MktWarehouseContact::getOpenid, openid).last("limit 1"));
        if (contact != null) {
            if (!Integer.valueOf(1).equals(contact.getStatus())) throw new BizException(403, "云仓联系人已停用，请联系园区运营");
            return warehouseResult(warehouses.selectById(contact.getWarehouseId()));
        }
        MktWarehouse existing = warehouses.selectOne(new LambdaQueryWrapper<MktWarehouse>()
                .eq(MktWarehouse::getContactOpenid, openid).last("limit 1"));
        if (existing != null) {
            JwtAccountService.assertWarehouseActive(existing);
            addOwner(existing, openid, existing.getPhone());
            return warehouseResult(existing);
        }
        if (phone != null) {
            if (promoters.selectCount(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getPhone, phone)) > 0)
                throw new BizException(409, "该手机号已绑定伙伴身份，请选择园区伙伴身份登录");
            List<MktWarehouse> matches = warehouses.selectList(new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getPhone, phone));
            if (matches.size() > 1) throw new BizException(409, "该手机号关联多个云仓，请联系园区运营核验");
            if (!matches.isEmpty()) {
                existing = matches.get(0);
                JwtAccountService.assertWarehouseActive(existing);
                phoneBindingGuard.assertCanBind("wh", existing.getId());
                if (StringUtils.hasText(existing.getContactOpenid())
                        || contacts.selectCount(new LambdaQueryWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getWarehouseId, existing.getId())) > 0)
                    throw new BizException(409, "该云仓已绑定其他微信，请使用原微信登录");
                int changed = warehouses.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                        .eq(MktWarehouse::getId, existing.getId()).isNull(MktWarehouse::getContactOpenid)
                        .between(MktWarehouse::getJoinStatus, 1, 6).set(MktWarehouse::getContactOpenid, openid));
                if (changed != 1) throw new BizException(409, "云仓状态或微信绑定已变化，请重新登录");
                existing.setContactOpenid(openid);
                addOwner(existing, openid, phone);
                audit.log("warehouse.bind", "warehouse", existing.getId(), "微信授权手机号绑定；同意用户协议和隐私政策 v1");
                return warehouseResult(existing);
            }
            if (contacts.selectCount(new LambdaQueryWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getPhone, phone)) > 0)
                throw new BizException(409, "该手机号已是云仓联系人，请使用原微信登录");
        }
        MktWarehouse created = new MktWarehouse();
        created.setCode("WH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
        created.setName(MktSelfProfileService.PENDING_WAREHOUSE_NAME);
        created.setContactOpenid(openid); created.setPhone(phone);
        created.setRemark("微信快捷申请；待补充联系资料和运营资质审核");
        onboarding.apply(created);
        // apply persists the transition to qualifying but its input entity keeps the earlier value.
        created.setJoinStatus(MktWarehouseOnboardingService.JS_QUALIFYING);
        addOwner(created, openid, phone);
        audit.log("warehouse.wechat.register", "warehouse", created.getId(), "微信快捷注册；同意用户协议和隐私政策 v1；资质待审核");
        return warehouseResult(created);
    }

    private void addOwner(MktWarehouse warehouse, String openid, String phone) {
        MktWarehouseContact contact = new MktWarehouseContact();
        contact.setWarehouseId(warehouse.getId()); contact.setOpenid(openid); contact.setPhone(phone);
        contact.setName(warehouse.getContact()); contact.setRole("owner"); contact.setStatus(1); contact.setProjectId(warehouse.getProjectId());
        contacts.insert(contact);
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

    private Map<String, Object> warehouseResult(MktWarehouse warehouse) {
        JwtAccountService.assertWarehouseActive(warehouse);
        Map<String, Object> result = new HashMap<>();
        result.put("registered", true); result.put("token", jwt.issueForIdentity("wh", warehouse.getId()));
        result.put("warehouseId", warehouse.getId()); result.put("warehouse", warehouse);
        return result;
    }
}
