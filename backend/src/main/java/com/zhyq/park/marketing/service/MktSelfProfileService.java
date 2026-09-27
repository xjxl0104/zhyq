package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.auth.JwtAccountService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktCredential;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.MktCredentialMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseContactMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Self-reported contact details never bind or merge a WeChat/business identity. */
@Service
@RequiredArgsConstructor
public class MktSelfProfileService {
    public static final String PENDING_WAREHOUSE_NAME = "待完善云仓";
    private final MktPromoterMapper promoters;
    private final MktWarehouseMapper warehouses;
    private final MktWarehouseContactMapper contacts;
    private final MktCredentialMapper credentials;
    private final MktPromoterService promoterService;
    private final MktAuditService audit;

    public boolean promoterPhoneEditable(MktPromoter promoter) {
        return !StringUtils.hasText(promoter.getOpenid()) && credential("mp", promoter.getId()) != null;
    }

    @Transactional
    public void updatePromoter(Long id, Map<String, String> body) {
        MktPromoter before = promoters.selectForUpdate(id);
        JwtAccountService.assertPromoterActive(before);
        String name = optionalText(body.get("name"), "姓名", 32);
        String avatar = optionalText(body.get("avatar"), "头像地址", 255);
        String phone = optionalPhone(body.get("phone"));
        boolean phoneChanged = phone != null && !phone.equals(before.getPhone());
        if (phoneChanged) {
            if (!promoterPhoneEditable(before)) throw new BizException("微信绑定手机号不能通过个人信息修改，请联系园区运营");
            reserveUnverifiedPhone("mp", id, phone);
        }
        if (name == null && avatar == null && !phoneChanged) return;
        LambdaUpdateWrapper<MktPromoter> update = new LambdaUpdateWrapper<MktPromoter>()
                .eq(MktPromoter::getId, id).in(MktPromoter::getStatus, 1, 3)
                .set(name != null, MktPromoter::getName, name)
                .set(avatar != null, MktPromoter::getAvatar, avatar)
                .set(phoneChanged, MktPromoter::getPhone, phone);
        if (phoneChanged) update.set(MktPromoter::getIsInternal, promoterService.isInternal(phone) ? 1 : 0);
        try {
            if (promoters.update(null, update) != 1) throw new BizException("资料或账号状态已变化，请刷新后重试");
        } catch (DuplicateKeyException e) { throw phoneConflict(); }
        audit.log("promoter.profile.update", "promoter", id, "本人补充个人信息；联系电话未经手机验证");
    }

    public Map<String, Object> warehouseProfile(Long id) {
        MktWarehouse warehouse = warehouses.selectById(id);
        JwtAccountService.assertWarehouseActive(warehouse);
        return warehouseProfile(warehouse);
    }

    @Transactional
    public Map<String, Object> updateWarehouse(Long id, Map<String, String> body) {
        MktWarehouse before = warehouses.selectOne(new LambdaQueryWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, id).last("FOR UPDATE"));
        JwtAccountService.assertWarehouseActive(before);
        if (!warehouseEditable(before)) throw new BizException("资质已审核，资料修改请联系园区运营");
        String warehouseName = body.containsKey("warehouseName")
                ? optionalText(body.get("warehouseName"), "云仓名称", 100) : before.getName();
        String name = body.containsKey("name") ? optionalText(body.get("name"), "姓名", 32) : before.getContact();
        String phone = optionalPhone(body.get("phone"));
        if (phone == null) phone = before.getPhone();
        prepareWarehousePhoneChange(before, phone);
        String savedName = warehouseName == null ? PENDING_WAREHOUSE_NAME : warehouseName;
        int changed = warehouses.update(null, new LambdaUpdateWrapper<MktWarehouse>()
                .eq(MktWarehouse::getId, id).in(MktWarehouse::getJoinStatus, 1, 2)
                .set(MktWarehouse::getName, savedName).set(MktWarehouse::getContact, name)
                .set(MktWarehouse::getPhone, phone).setSql("version = version + 1"));
        if (changed != 1) throw new BizException("资质或资料已变化，请刷新后重试");
        before.setName(savedName); before.setContact(name); before.setPhone(phone);
        audit.log("warehouse.profile.update", "warehouse", id, "本人补充个人信息；不变更资质审核状态");
        return warehouseProfile(before);
    }

    /** Shared by the full application editor so it cannot bypass the same phone-identity guard. */
    public void prepareWarehousePhoneChange(MktWarehouse before, String phone) {
        if (Objects.equals(before.getPhone(), phone)) return;
        if (!warehousePhoneEditable(before)) throw new BizException("微信绑定手机号不能通过个人信息修改，请联系园区运营");
        reserveUnverifiedPhone("wh", before.getId(), optionalPhone(phone));
    }

    private Map<String, Object> warehouseProfile(MktWarehouse warehouse) {
        String name = PENDING_WAREHOUSE_NAME.equals(warehouse.getName()) ? null : warehouse.getName();
        Map<String, Object> profile = new HashMap<>();
        profile.put("warehouseId", warehouse.getId()); profile.put("warehouseName", name);
        profile.put("name", warehouse.getContact()); profile.put("phone", warehouse.getPhone());
        profile.put("joinStatus", warehouse.getJoinStatus());
        profile.put("profileEditable", warehouseEditable(warehouse));
        profile.put("phoneEditable", warehouseEditable(warehouse) && warehousePhoneEditable(warehouse));
        profile.put("profileComplete", StringUtils.hasText(name) && StringUtils.hasText(warehouse.getContact())
                && isMobilePhone(warehouse.getPhone()));
        return profile;
    }

    private boolean warehousePhoneEditable(MktWarehouse warehouse) {
        return !StringUtils.hasText(warehouse.getContactOpenid())
                && contacts.selectCount(new LambdaQueryWrapper<MktWarehouseContact>()
                    .eq(MktWarehouseContact::getWarehouseId, warehouse.getId())) == 0
                && credential("wh", warehouse.getId()) != null;
    }

    private static boolean warehouseEditable(MktWarehouse warehouse) {
        return Integer.valueOf(1).equals(warehouse.getJoinStatus()) || Integer.valueOf(2).equals(warehouse.getJoinStatus());
    }

    private void reserveUnverifiedPhone(String type, Long id, String phone) {
        if (phone == null) return;
        boolean occupied = promoters.selectCount(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getPhone, phone)
                    .ne("mp".equals(type), MktPromoter::getId, id)) > 0
                || warehouses.selectCount(new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getPhone, phone)
                    .ne("wh".equals(type), MktWarehouse::getId, id)) > 0
                || contacts.selectCount(new LambdaQueryWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getPhone, phone)) > 0;
        if (occupied) throw phoneConflict();
        // Persist the unverified marker in the same transaction as the business profile.
        // A later verified-phone login must not claim this password account.
        try {
            if (credentials.update(null, new LambdaUpdateWrapper<MktCredential>()
                    .eq(MktCredential::getIdentityType, type).eq(MktCredential::getIdentityId, id)
                    .eq(MktCredential::getStatus, 1).set(MktCredential::getRegistrationPhone, phone)) != 1)
                throw new BizException("账号状态已变化，请重新登录后重试");
        } catch (DuplicateKeyException e) { throw phoneConflict(); }
    }

    private MktCredential credential(String type, Long id) {
        MktCredential credential = credentials.selectOne(new LambdaQueryWrapper<MktCredential>()
                .eq(MktCredential::getIdentityType, type).eq(MktCredential::getIdentityId, id));
        return credential != null && Integer.valueOf(1).equals(credential.getStatus()) ? credential : null;
    }

    public static boolean isMobilePhone(String phone) { return phone != null && phone.matches("^1\\d{10}$"); }

    public static String optionalPhone(String value) {
        String phone = optionalText(value, "手机号", 11);
        if (phone != null && !isMobilePhone(phone)) throw new BizException("手机号格式不正确");
        return phone;
    }

    public static String optionalText(String value, String label, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (!StringUtils.hasText(normalized)) return null;
        if (normalized.length() > maxLength) throw new BizException(label + "不能超过 " + maxLength + " 字");
        return normalized;
    }

    private static BizException phoneConflict() {
        return new BizException(409, "该手机号已有业务档案，请使用原账号或联系园区运营；不能凭填写手机号合并身份");
    }
}
