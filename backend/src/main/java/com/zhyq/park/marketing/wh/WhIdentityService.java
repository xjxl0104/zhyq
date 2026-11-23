package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.auth.JwtAccountService;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.MktWarehouseContactMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mp.WxPhoneBindingGuard;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktSelfProfileService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A login belongs to a contact, never to everyone sharing a warehouse. */
@Service
@RequiredArgsConstructor
public class WhIdentityService {
    private final MktWarehouseContactMapper contacts;
    private final MktWarehouseMapper warehouses;
    private final MktWarehouseOnboardingService onboarding;
    private final JwtService jwt;
    private final WxPhoneBindingGuard phoneBindingGuard;
    private final MktAuditService audit;
    private final com.zhyq.park.marketing.service.WhPhoneClaimService phoneClaims;

    public record Identity(MktWarehouse warehouse, MktWarehouseContact contact) {}

    @Transactional
    public Map<String,Object> quickLoginIdentity(String openid, String phone, String name) {
        return result(phoneIdentity(openid, phone, name, true));
    }

    @Transactional
    public Identity bindVerifiedPhone(String openid, String phone, String name) {
        return phoneIdentity(openid, phone, name, false);
    }

    private Identity phoneIdentity(String openid, String phone, String name, boolean allowCreate) {
        if (!StringUtils.hasText(openid)) throw new BizException(400, "缺少已验证的微信身份");
        phone = requirePhone(phone);
        name = requireName(name);
        lockPhone(phone);
        MktWarehouseContact byOpenid = contacts.selectOne(new LambdaQueryWrapper<MktWarehouseContact>()
                .eq(MktWarehouseContact::getOpenid, openid).last("limit 1"));
        if (byOpenid != null) {
            active(byOpenid);
            if ("owner".equals(byOpenid.getRole()) && !StringUtils.hasText(byOpenid.getPhone()))
                return completeLegacyOwner(byOpenid,openid,phone,name);
            if (!phone.equals(byOpenid.getPhone())) throw new BizException(409, "本次授权手机号与绑定账号不一致，请联系云仓主账号核验");
        }
        List<MktWarehouseContact> phoneContacts = contacts.selectList(new LambdaQueryWrapper<MktWarehouseContact>()
                .eq(MktWarehouseContact::getPhone, phone));
        if (phoneContacts.size() > 1) throw new BizException(409, "该手机号关联多个云仓账号，请联系园区运营核验");
        List<MktWarehouse> phoneWarehouses = warehouses.selectList(new LambdaQueryWrapper<MktWarehouse>()
                .eq(MktWarehouse::getPhone, phone));
        if (phoneWarehouses.size() > 1) throw new BizException(409, "该手机号关联多个云仓，请联系园区运营核验");
        MktWarehouseContact contact = phoneContacts.isEmpty() ? byOpenid : phoneContacts.get(0);
        if (contact != null) {
            active(contact);
            if (byOpenid != null && !Objects.equals(byOpenid.getId(), contact.getId())) throw conflict();
            if (!phoneWarehouses.isEmpty() && (!Objects.equals(phoneWarehouses.get(0).getId(), contact.getWarehouseId())
                    || !"owner".equals(contact.getRole()))) throw conflict();
            MktWarehouse warehouse = warehouses.selectForUpdate(contact.getWarehouseId());
            JwtAccountService.assertWarehouseActive(warehouse);
            // An owner may have changed this member's phone/status while we waited for the warehouse lock.
            contact = contacts.selectById(contact.getId());
            active(contact);
            if (!phone.equals(contact.getPhone()) || !Objects.equals(warehouse.getId(),contact.getWarehouseId())) throw conflict();
            if ("owner".equals(contact.getRole()) && !phone.equals(warehouse.getPhone())) throw conflict();
            bindOpenid(contact, warehouse, openid);
            updateName(contact, warehouse, name);
            return new Identity(warehouse, contact);
        }
        if (!phoneWarehouses.isEmpty()) {
            MktWarehouse warehouse = warehouses.selectForUpdate(phoneWarehouses.get(0).getId());
            JwtAccountService.assertWarehouseActive(warehouse);
            if (!phone.equals(warehouse.getPhone())) throw conflict();
            contact = ownerContact(warehouse);
            active(contact);
            if (!phone.equals(contact.getPhone())) throw conflict();
            bindOpenid(contact, warehouse, openid);
            updateName(contact, warehouse, name);
            audit.log("warehouse.bind", "warehouse", warehouse.getId(), "主账号使用微信验证手机号登录");
            return new Identity(warehouse, contact);
        }
        if (!allowCreate) return null;
        MktWarehouse warehouse = new MktWarehouse();
        warehouse.setCode("WH-" + UUID.randomUUID().toString().replace("-", "").substring(0,20).toUpperCase(Locale.ROOT));
        warehouse.setName(MktSelfProfileService.PENDING_WAREHOUSE_NAME);
        warehouse.setContact(name);
        warehouse.setPhone(phone);
        warehouse.setContactOpenid(openid);
        warehouse.setRemark("微信实名及手机号授权申请，待补充云仓资料");
        onboarding.apply(warehouse);
        warehouse.setJoinStatus(MktWarehouseOnboardingService.JS_QUALIFYING);
        contact = ownerContact(warehouse);
        audit.log("warehouse.wechat.register", "warehouse", warehouse.getId(), "实名及微信手机号授权注册主账号");
        return new Identity(warehouse, contact);
    }

    /** Completes an existing WeChat owner, never adopts another person's warehouse by a typed phone. */
    private Identity completeLegacyOwner(MktWarehouseContact snapshot, String openid, String phone, String name) {
        MktWarehouse warehouse = warehouses.selectForUpdate(snapshot.getWarehouseId());
        JwtAccountService.assertWarehouseActive(warehouse);
        MktWarehouseContact contact = contacts.selectById(snapshot.getId());
        active(contact);
        if (!"owner".equals(contact.getRole()) || !Objects.equals(contact.getWarehouseId(),warehouse.getId())
                || !Objects.equals(openid,contact.getOpenid()) || !Objects.equals(openid,warehouse.getContactOpenid())) throw conflict();
        if ((StringUtils.hasText(contact.getPhone()) && !phone.equals(contact.getPhone()))
                || (StringUtils.hasText(warehouse.getPhone()) && !phone.equals(warehouse.getPhone()))) throw conflict();
        phoneClaims.reserve(phone,warehouse.getId(),contact.getId());
        if (contacts.update(null,new LambdaUpdateWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getId,contact.getId())
                .eq(MktWarehouseContact::getStatus,1).eq(MktWarehouseContact::getOpenid,openid)
                .set(MktWarehouseContact::getPhone,phone).set(MktWarehouseContact::getName,name)) != 1) throw conflict();
        if (warehouses.update(null,new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId,warehouse.getId())
                .eq(MktWarehouse::getContactOpenid,openid).set(MktWarehouse::getPhone,phone).set(MktWarehouse::getContact,name)) != 1) throw conflict();
        contact.setPhone(phone);contact.setName(name);warehouse.setPhone(phone);warehouse.setContact(name);
        audit.log("warehouse.owner.complete", "warehouse", warehouse.getId(), "原微信主账号使用微信验证手机号补全资料");
        return new Identity(warehouse,contact);
    }

    @Transactional
    public Identity findByOpenid(String openid, Long requestedWarehouseId) {
        if (!StringUtils.hasText(openid)) return null;
        MktWarehouseContact contact = contacts.selectOne(new LambdaQueryWrapper<MktWarehouseContact>()
                .eq(MktWarehouseContact::getOpenid, openid).last("limit 1"));
        MktWarehouse warehouse;
        if (contact == null) {
            warehouse = warehouses.selectOne(new LambdaQueryWrapper<MktWarehouse>()
                    .eq(MktWarehouse::getContactOpenid, openid).last("limit 1"));
            if (warehouse == null || !StringUtils.hasText(warehouse.getPhone()) || !StringUtils.hasText(warehouse.getContact())) return null;
            lockPhone(requirePhone(warehouse.getPhone()));
            warehouse = warehouses.selectForUpdate(warehouse.getId());
            if (!Objects.equals(openid,warehouse.getContactOpenid())) return null;
            contact = ownerContact(warehouse);
        } else {
            active(contact); // An inactive openid must never fall through to legacy ownership.
            if (!com.zhyq.park.marketing.service.LoginProfileRequirements.complete(contact.getName(),contact.getPhone())) return null;
            lockPhone(requirePhone(contact.getPhone()));
            warehouse = warehouses.selectForUpdate(contact.getWarehouseId());
            contact = contacts.selectById(contact.getId());
            active(contact);
            if (!Objects.equals(openid,contact.getOpenid()) || !Objects.equals(warehouse.getId(),contact.getWarehouseId())) return null;
        }
        active(contact);
        JwtAccountService.assertWarehouseActive(warehouse);
        if (requestedWarehouseId != null && !Objects.equals(requestedWarehouseId, warehouse.getId())) return null;
        if (!com.zhyq.park.marketing.service.LoginProfileRequirements.complete(contact.getName(),contact.getPhone())) return null;
        return new Identity(warehouse, contact);
    }

    /** Called only after an owner password credential has been verified. */
    @Transactional
    public String issueOwnerToken(Long warehouseId) {
        MktWarehouse existing = warehouses.selectById(warehouseId);
        JwtAccountService.assertWarehouseActive(existing);
        lockPhone(requirePhone(existing.getPhone()));
        MktWarehouse warehouse = warehouses.selectForUpdate(warehouseId);
        MktWarehouseContact owner = ownerContact(warehouse);
        active(owner);
        if (Boolean.TRUE.equals(owner.getPasswordResetRequired())) throw new BizException(403,"主账号手机号已变更，请使用新手机号微信授权登录后重新设置密码");
        requireName(owner.getName());
        return issueToken(owner);
    }

    public String issueToken(MktWarehouseContact contact) {
        active(contact);
        return jwt.issueForIdentity("wh_contact", contact.getId());
    }

    public Map<String,Object> result(Identity identity) {
        if (identity == null) return Map.of("registered", false);
        MktWarehouseContact contact = identity.contact();
        Map<String,Object> result = new HashMap<>();
        result.put("registered", true);
        result.put("token", issueToken(contact));
        result.put("warehouseId", identity.warehouse().getId());
        result.put("warehouse", safeWarehouse(identity.warehouse()));
        result.put("contactId", contact.getId());
        result.put("warehouseRole", contact.getRole());
        result.put("account", profile(contact, identity.warehouse()));
        return result;
    }

    public static MktWarehouse safeWarehouse(MktWarehouse source) {
        MktWarehouse out = new MktWarehouse();
        out.setId(source.getId()); out.setName(source.getName()); out.setCode(source.getCode());
        out.setJoinStatus(source.getJoinStatus()); out.setErpStatus(source.getErpStatus()); out.setOrderMode(source.getOrderMode());
        return out;
    }

    public static Map<String,Object> profile(MktWarehouseContact contact, MktWarehouse warehouse) {
        Map<String,Object> out = new HashMap<>();
        out.put("id", contact.getId()); out.put("contactId", contact.getId()); out.put("warehouseId", warehouse.getId());
        out.put("warehouseName", warehouse.getName()); out.put("name", contact.getName()); out.put("phone", contact.getPhone());
        out.put("warehouseRole", contact.getRole()); out.put("isOwner", "owner".equals(contact.getRole()));
        out.put("profileEditable", true); out.put("phoneEditable", false); out.put("status", contact.getStatus());
        out.put("profileComplete", StringUtils.hasText(warehouse.getName()) && !MktSelfProfileService.PENDING_WAREHOUSE_NAME.equals(warehouse.getName()));
        return out;
    }

    public void lockPhone(String phone) {
        phoneClaims.lock(phone);
    }
    public void reservePhone(String phone, Long warehouseId, Long contactId) { phoneClaims.reserve(phone,warehouseId,contactId); }

    /** Shared by authorized administration and the owner's own profile editor. */
    @Transactional
    public MktWarehouse maintainOwner(Long warehouseId, String name, String phone, boolean adminVerifiedPhoneChange) {
        name = requireName(name); phone = requirePhone(phone);
        lockPhone(phone);
        MktWarehouse before = warehouses.selectForUpdate(warehouseId);
        JwtAccountService.assertWarehouseActive(before);
        boolean phoneChanged = !Objects.equals(before.getPhone(),phone);
        if (phoneChanged && !adminVerifiedPhoneChange) throw new BizException(403,"登录手机号需由园区运营核验后修改");
        MktWarehouseContact owner = ownerContact(before);
        phoneClaims.reserve(phone,warehouseId,owner.getId());
        var update = new LambdaUpdateWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getId,owner.getId())
                .eq(MktWarehouseContact::getWarehouseId,warehouseId).eq(MktWarehouseContact::getRole,"owner")
                .set(MktWarehouseContact::getName,name).set(MktWarehouseContact::getPhone,phone)
                .set(MktWarehouseContact::getProjectId,before.getProjectId()).set(MktWarehouseContact::getTenantId,before.getTenantId());
        var warehouseUpdate = new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId,warehouseId)
                .set(MktWarehouse::getContact,name).set(MktWarehouse::getPhone,phone);
        if (phoneChanged) {
            update.set(MktWarehouseContact::getOpenid,null).set(MktWarehouseContact::getUnionid,null)
                    .set(MktWarehouseContact::getPhoneBindingApproved,true).setSql("auth_revision=auth_revision+1");
            update.set(MktWarehouseContact::getPasswordResetRequired,true);
            warehouseUpdate.set(MktWarehouse::getContactOpenid,null);
        }
        if (contacts.update(null,update) != 1 || warehouses.update(null,warehouseUpdate) != 1) throw conflict();
        audit.log("warehouse.owner.profile", "warehouse", warehouseId, phoneChanged ? "运营核验更换主账号手机号，旧微信登录已失效" : "同步主账号姓名资料");
        return before;
    }

    public boolean ownerPasswordResetRequired(Long warehouseId) {
        return Boolean.TRUE.equals(passwordOwner(warehouseId).getPasswordResetRequired());
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean beginOwnerPasswordSetup(Long warehouseId) {
        warehouses.selectForUpdate(warehouseId);
        MktWarehouseContact owner = passwordOwner(warehouseId);
        boolean reset = Boolean.TRUE.equals(owner.getPasswordResetRequired());
        if (reset && !StringUtils.hasText(owner.getOpenid())) throw new BizException(403,"请先使用新手机号完成微信授权登录");
        return reset;
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void finishOwnerPasswordSetup(Long warehouseId) {
        MktWarehouseContact owner = passwordOwner(warehouseId);
        if (!Boolean.TRUE.equals(owner.getPasswordResetRequired())) return;
        if (contacts.update(null,new LambdaUpdateWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getId,owner.getId())
                .eq(MktWarehouseContact::getPasswordResetRequired,true).set(MktWarehouseContact::getPasswordResetRequired,false)
                .setSql("auth_revision=auth_revision+1")) != 1) throw conflict();
        audit.log("warehouse.owner.password.reset", "warehouse", warehouseId, "新手机号微信验证后的主账号重设密码，旧密码登录已撤销");
    }

    private MktWarehouseContact passwordOwner(Long warehouseId) {
        WhAuthContext.requireOwner();
        if (!Objects.equals(warehouseId,WhAuthContext.currentWarehouseId())) throw new BizException(403,"云仓身份不匹配");
        MktWarehouse warehouse = warehouses.selectById(warehouseId);
        JwtAccountService.assertWarehouseActive(warehouse);
        MktWarehouseContact contact = contacts.selectById(WhAuthContext.currentContactId());
        active(contact);
        if (!"owner".equals(contact.getRole()) || !Objects.equals(warehouseId,contact.getWarehouseId())
                || !Objects.equals(warehouse.getPhone(),contact.getPhone())) throw new BizException(403,"仅当前云仓主账号可以设置密码");
        if (Boolean.TRUE.equals(contact.getPasswordResetRequired())
                && (!StringUtils.hasText(contact.getOpenid()) || !Objects.equals(contact.getOpenid(),warehouse.getContactOpenid())))
            throw new BizException(403,"请先使用新手机号完成微信授权登录");
        return contact;
    }

    private MktWarehouseContact ownerContact(MktWarehouse warehouse) {
        JwtAccountService.assertWarehouseActive(warehouse);
        MktWarehouseContact owner = contacts.selectOne(new LambdaQueryWrapper<MktWarehouseContact>()
                .eq(MktWarehouseContact::getWarehouseId, warehouse.getId()).eq(MktWarehouseContact::getRole,"owner").last("limit 1"));
        if (owner != null) {
            if (!Objects.equals(owner.getPhone(), warehouse.getPhone())) throw new BizException(409, "主账号手机号与云仓档案不一致，请联系园区运营核验");
            return owner;
        }
        owner = new MktWarehouseContact();
        owner.setWarehouseId(warehouse.getId()); owner.setRole("owner"); owner.setStatus(1);
        owner.setPhone(warehouse.getPhone()); owner.setName(warehouse.getContact()); owner.setOpenid(warehouse.getContactOpenid());
        owner.setProjectId(warehouse.getProjectId()); owner.setTenantId(warehouse.getTenantId());
        try { contacts.insert(owner); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw conflict(); }
        return owner;
    }

    private void bindOpenid(MktWarehouseContact contact, MktWarehouse warehouse, String openid) {
        if (StringUtils.hasText(contact.getOpenid())) {
            if (!openid.equals(contact.getOpenid())) throw new BizException(409, "此账号已绑定其他微信，请联系云仓主账号核验");
            return;
        }
        if ("owner".equals(contact.getRole())) {
            if (!Boolean.TRUE.equals(contact.getPhoneBindingApproved())) phoneBindingGuard.assertCanBind("wh", warehouse.getId());
            if (StringUtils.hasText(warehouse.getContactOpenid()) && !openid.equals(warehouse.getContactOpenid())) throw conflict();
        }
        try {
            int changed = contacts.update(null, new LambdaUpdateWrapper<MktWarehouseContact>()
                    .eq(MktWarehouseContact::getId, contact.getId()).eq(MktWarehouseContact::getStatus,1)
                    .and(q -> q.isNull(MktWarehouseContact::getOpenid).or().eq(MktWarehouseContact::getOpenid,""))
                    .set(MktWarehouseContact::getOpenid, openid));
            if (changed != 1) throw conflict();
        } catch (org.springframework.dao.DuplicateKeyException e) { throw conflict(); }
        contact.setOpenid(openid);
        if ("owner".equals(contact.getRole())) {
            warehouses.update(null, new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId,warehouse.getId())
                    .set(MktWarehouse::getContactOpenid,openid));
            warehouse.setContactOpenid(openid);
        }
    }

    private void updateName(MktWarehouseContact contact, MktWarehouse warehouse, String name) {
        contacts.update(null, new LambdaUpdateWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getId,contact.getId())
                .set(MktWarehouseContact::getName,name));
        contact.setName(name);
        if ("owner".equals(contact.getRole())) {
            warehouses.update(null, new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId,warehouse.getId()).set(MktWarehouse::getContact,name));
            warehouse.setContact(name);
        }
    }

    public static String requireName(String name) {
        return com.zhyq.park.marketing.service.LoginProfileRequirements.requireName(name);
    }
    public static String requirePhone(String phone) {
        if (phone == null || !phone.trim().matches("^1\\d{10}$")) throw new BizException(400,"请使用微信验证的11位手机号");
        return phone.trim();
    }
    public static void active(MktWarehouseContact contact) {
        if (contact == null || !Integer.valueOf(1).equals(contact.getStatus())) throw new BizException(403,"云仓账号不存在或已停用，请联系主账号");
        if (!("owner".equals(contact.getRole()) || "member".equals(contact.getRole()))) throw new BizException(403,"云仓账号权限无效");
    }
    private static BizException conflict() { return new BizException(409,"云仓账号绑定存在冲突，请联系园区运营核验"); }
}
