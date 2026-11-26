package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktPublicSeaMeta;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktPublicSeaMetaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** All referral channels resolve the partner on the server and share attribution checks. */
@Service
@RequiredArgsConstructor
public class MktReferralService {
    private static final Set<String> INTERNAL_EXCLUSIVE_SEA_SOURCES = Set.of(
            "CALC_HOT", "FEIYU_AD", "AMAP_POI", "CHANMAMA", "TUNGEE_JOB");
    private static final Set<String> PROTECTED_SEA_STATUSES = Set.of(
            "CLAIMED_ADMIN", "CLAIMED_WH", "FEED_WH", "SIGNED");

    private final CustomerMapper customers;
    private final MktPromoterMapper promoters;
    private final MktLockService locks;
    private final BizSettings settings;
    private final MktAuditService audit;
    private final MktCustomerAssignmentService assignment;
    private final MktCustomerIntakeGuard intakeGuard;
    private final MktCustomerProgressService progress;

    /** 非构造注入以保持存量单测兼容；生产态由 Spring 自动装配，拦截外部伙伴截胡内部公海保护单。 */
    @Autowired(required = false)
    private MktPublicSeaMetaMapper seaMetas;

    void setSeaMetas(MktPublicSeaMetaMapper seaMetas) {
        this.seaMetas = seaMetas;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> create(Long promoterId, Map<String,Object> body) {
        return createInternal(promoterId, null, body);
    }

    /** Admin controller has already checked this project's tenant and the operator's permission. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> createForProject(Long promoterId, Long projectId, Map<String,Object> body) {
        return createInternal(promoterId, projectId, body);
    }

    private Map<String,Object> createInternal(Long promoterId, Long projectId, Map<String,Object> body) {
        MktPromoter me = promoters.selectForUpdate(promoterId);
        if (me == null || !Integer.valueOf(1).equals(me.getStatus())) throw new BizException(403, "伙伴不存在或状态异常，不能推荐客户");
        if (projectId != null && me.getProjectId() != null && !projectId.equals(me.getProjectId())) throw new BizException(403, "不能跨园区指定推荐伙伴");
        Long effectiveProject = projectId == null ? me.getProjectId() : projectId;
        String name = text(body, "name", 100, "客户名称");
        if (name == null) throw new BizException(400, "客户名称必填");
        String phone = text(body, "phone", 32, "联系手机");
        if (phone != null && !phone.matches("^1\\d{10}$")) throw new BizException(400, "请填写正确的 11 位手机号，或留空");
        if (phone != null && phone.equals(me.getPhone())) throw new BizException(400, "不能自我推荐");
        int cap = settings.getInt("marketing", "daily_referral_cap", 20);
        if (customers.countReferralsSince(promoterId, LocalDate.now().atStartOfDay()) >= cap)
            throw new BizException(429, "今日推荐已达上限 " + cap + " 个");
        Long tenantId = me.getTenantId() == null ? com.zhyq.park.common.config.MyMetaObjectHandler.DEFAULT_TENANT_ID : me.getTenantId();
        intakeGuard.lock(tenantId);
        Customer existingByName = null;
        if (customers.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getTenantId, tenantId).eq(Customer::getName, name)) > 0) {
            existingByName = customers.selectOne(new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getTenantId, tenantId).eq(Customer::getName, name).last("limit 1"));
            if (existingByName == null || !isReclaimablePublicCustomer(existingByName)) {
                throw new BizException(409, "该客户已在系统中(已被推荐)");
            }
        }
        Customer existingByPhone = null;
        if (phone != null && customers.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getPhone, phone)) > 0) {
            existingByPhone = customers.selectOne(new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getTenantId, tenantId).eq(Customer::getPhone, phone).last("limit 1"));
            if (existingByPhone == null || !isReclaimablePublicCustomer(existingByPhone)) {
                throw new BizException(409, "该客户已在系统中(已被推荐)");
            }
        }
        if (existingByName != null && existingByPhone != null && !java.util.Objects.equals(existingByName.getId(), existingByPhone.getId())) {
            throw new BizException(409, "该客户已在系统中(已被推荐)");
        }
        Customer existing = existingByName != null ? existingByName : existingByPhone;

        String contact = text(body, "contact", 100, "联系人");
        String brandName = text(body, "brandName", 100, "品牌名称");
        String industry = text(body, "industry", 100, "行业");
        String demand = text(body, "demand", 200, "需求");
        String remark = text(body, "remark", 1000, "备注");
        String requirements = MktReferralRequirements.normalize(body.get("requirements"));
        Object rawType = body.get("serviceType");
        int type;
        try { type = rawType == null ? 2 : Integer.parseInt(rawType.toString()); }
        catch (NumberFormatException e) { throw new BizException(400, "需求类型不正确"); }
        if (type < 1 || type > 4) throw new BizException(400, "需求类型不正确");
        Long wid = null;
        Object rawWarehouse = body.get("warehouseId");
        if (rawWarehouse != null && StringUtils.hasText(rawWarehouse.toString())) {
            try { wid = Long.valueOf(rawWarehouse.toString()); }
            catch (NumberFormatException e) { throw new BizException(400, "意向云仓不正确"); }
            if (type == 4) throw new BizException(400, "园区入驻需求不选择云仓");
            var warehouse = assignment.requireAvailableWarehouse(wid, effectiveProject);
            Long warehouseTenant = warehouse.getTenantId() == null
                    ? com.zhyq.park.common.config.MyMetaObjectHandler.DEFAULT_TENANT_ID : warehouse.getTenantId();
            if (!tenantId.equals(warehouseTenant)) throw new BizException(403, "意向云仓与推荐伙伴不属于同一运营主体");
        }

        if (existing != null) {
            assignment.assertNoActiveContracts(existing.getId());
            var lock = locks.prelock(existing.getId(), promoterId);
            LambdaUpdateWrapper<Customer> reclaimUpdate = new LambdaUpdateWrapper<Customer>()
                    .eq(Customer::getId, existing.getId())
                    .set(Customer::getStatus, 1)
                    .set(Customer::getServiceType, type)
                    .set(Customer::getBizLine, type == 4 ? 1 : 2)
                    .set(Customer::getAttributionNote, "公海重报接管，伙伴 " + me.getInviteCode())
                    .set(phone != null, Customer::getPhone, phone)
                    .set(contact != null, Customer::getContact, contact)
                    .set(brandName != null, Customer::getBrandName, brandName)
                    .set(industry != null, Customer::getIndustry, industry)
                    .set(demand != null, Customer::getDemandArea, demand)
                    .set(remark != null, Customer::getRemark, remark)
                    .set(remark != null, Customer::getIntakeRemark, remark)
                    .set(body.get("requirements") != null, Customer::getFulfillmentRequirements, requirements)
                    .set(wid != null, Customer::getIntendedWarehouseId, wid);
            customers.update(null, reclaimUpdate);
            audit.log("referral.reclaim", "customer", existing.getId(), "公海客户重报接管，伙伴 " + promoterId);
            Map<String, Object> reclaimed = new HashMap<>();
            reclaimed.put("customerId", existing.getId());
            reclaimed.put("lockStatus", lock.getStatus());
            reclaimed.put("lockUntil", lock.getLockUntil());
            reclaimed.put("prelockUntil", lock.getLockUntil());
            return reclaimed;
        }

        Customer c = new Customer();
        c.setTenantId(tenantId); c.setProjectId(effectiveProject);
        c.setName(name); c.setPhone(phone); c.setContact(contact);
        c.setBrandName(brandName);
        c.setIndustry(industry);
        c.setDemandArea(demand);
        c.setRemark(remark);
        c.setIntakeRemark(c.getRemark());
        c.setFulfillmentRequirements(requirements);
        c.setServiceType(type); c.setBizLine(type == 4 ? 1 : 2);
        if (wid != null) {
            c.setIntendedWarehouseId(wid);
        }
        c.setWarehouseAssignmentStatus(MktCustomerAssignmentService.UNASSIGNED);
        c.setReferrerId(promoterId); c.setStatus(1); c.setOwner("待分配");
        c.setAttributionNote("客户推荐，伙伴 " + me.getInviteCode());
        try { customers.insert(c); }
        catch (DuplicateKeyException e) { throw new BizException(409, "该客户已在系统中(已被推荐)"); }
        progress.created(c.getId());
        var lock = locks.prelock(c.getId(), promoterId);
        audit.log("referral.create", "customer", c.getId(), "客户推荐，伙伴 " + promoterId);
        Map<String,Object> result = new HashMap<>();
        result.put("customerId", c.getId()); result.put("lockStatus", lock.getStatus());
        result.put("lockUntil", lock.getLockUntil());
        // Old published clients still render this field while the miniapp update is reviewed.
        result.put("prelockUntil", lock.getLockUntil());
        return result;
    }

    private boolean isReclaimablePublicCustomer(Customer c) {
        if (c.getReferrerId() != null || Integer.valueOf(2).equals(c.getStatus())) {
            return false;
        }
        if (seaMetas != null && c.getId() != null) {
            MktPublicSeaMeta meta = seaMetas.selectById(c.getId());
            if (meta != null) {
                if (PROTECTED_SEA_STATUSES.contains(meta.getClaimStatus())
                        || (meta.getVisitProtectedUntil() != null && meta.getVisitProtectedUntil().isAfter(LocalDateTime.now()))) {
                    throw new BizException(409, "该客户正由园区内部专员或加盟云仓保护跟进中，不可外部重报接管");
                }
                if (meta.getSeaSource() != null && INTERNAL_EXCLUSIVE_SEA_SOURCES.contains(meta.getSeaSource())) {
                    throw new BizException(409, "该客户属园区自投流/付费拓客专属公海线索，不可外部重报接管");
                }
            }
        }
        return true;
    }

    private static String text(Map<String,Object> body, String key, int max, String label) {
        return MktReferralRequirements.text(body.get(key), max, label);
    }
}
