package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** All referral channels resolve the partner on the server and share attribution checks. */
@Service
@RequiredArgsConstructor
public class MktReferralService {
    private final CustomerMapper customers;
    private final MktPromoterMapper promoters;
    private final MktLockService locks;
    private final BizSettings settings;
    private final MktAuditService audit;
    private final MktCustomerAssignmentService assignment;
    private final MktCustomerIntakeGuard intakeGuard;
    private final MktCustomerProgressService progress;

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
        if (me == null || !Integer.valueOf(1).equals(me.getStatus())) throw new BizException("伙伴不存在或状态异常，不能推荐客户");
        if (projectId != null && me.getProjectId() != null && !projectId.equals(me.getProjectId())) throw new BizException("不能跨园区指定推荐伙伴");
        Long effectiveProject = projectId == null ? me.getProjectId() : projectId;
        String name = text(body, "name", 100, "客户名称");
        if (name == null) throw new BizException("客户名称必填");
        String phone = text(body, "phone", 32, "联系手机");
        if (phone != null && !phone.matches("^1\\d{10}$")) throw new BizException("请填写正确的 11 位手机号，或留空");
        if (phone != null && phone.equals(me.getPhone())) throw new BizException("不能自我推荐");
        int cap = settings.getInt("marketing", "daily_referral_cap", 20);
        if (customers.countReferralsSince(promoterId, LocalDate.now().atStartOfDay()) >= cap)
            throw new BizException("今日推荐已达上限 " + cap + " 个");
        Long tenantId = me.getTenantId() == null ? com.zhyq.park.common.config.MyMetaObjectHandler.DEFAULT_TENANT_ID : me.getTenantId();
        intakeGuard.lock(tenantId);
        if (customers.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getTenantId, tenantId).eq(Customer::getName, name)) > 0)
            throw new BizException("该客户已在系统中(已被推荐)");
        if (phone != null && customers.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getPhone, phone)) > 0)
            throw new BizException("该客户已在系统中(已被推荐)");
        Customer c = new Customer();
        c.setTenantId(tenantId); c.setProjectId(effectiveProject);
        c.setName(name); c.setPhone(phone); c.setContact(text(body, "contact", 100, "联系人"));
        c.setBrandName(text(body, "brandName", 100, "品牌名称"));
        c.setIndustry(text(body, "industry", 100, "行业"));
        c.setDemandArea(text(body, "demand", 200, "需求"));
        c.setRemark(text(body, "remark", 1000, "备注"));
        c.setIntakeRemark(c.getRemark());
        c.setFulfillmentRequirements(MktReferralRequirements.normalize(body.get("requirements")));
        Object rawType = body.get("serviceType");
        int type;
        try { type = rawType == null ? 2 : Integer.parseInt(rawType.toString()); }
        catch (NumberFormatException e) { throw new BizException("需求类型不正确"); }
        if (type < 1 || type > 4) throw new BizException("需求类型不正确");
        c.setServiceType(type); c.setBizLine(type == 4 ? 1 : 2);
        Object rawWarehouse = body.get("warehouseId");
        if (rawWarehouse != null && StringUtils.hasText(rawWarehouse.toString())) {
            Long wid;
            try { wid = Long.valueOf(rawWarehouse.toString()); }
            catch (NumberFormatException e) { throw new BizException("意向云仓不正确"); }
            if (type == 4) throw new BizException("园区入驻需求不选择云仓");
            assignment.requireAvailableWarehouse(wid, effectiveProject); c.setIntendedWarehouseId(wid);
        }
        c.setWarehouseAssignmentStatus(MktCustomerAssignmentService.UNASSIGNED);
        c.setReferrerId(promoterId); c.setStatus(1); c.setOwner("待分配");
        c.setAttributionNote("客户推荐，伙伴 " + me.getInviteCode());
        try { customers.insert(c); }
        catch (DuplicateKeyException e) { throw new BizException("该客户已在系统中(已被推荐)"); }
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

    private static String text(Map<String,Object> body, String key, int max, String label) {
        return MktReferralRequirements.text(body.get(key), max, label);
    }
}
