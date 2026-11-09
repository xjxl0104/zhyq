package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.building.entity.Project;
import com.zhyq.park.building.mapper.ProjectMapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Backend intake never accepts entity ids, status, tenant or assignment fields from the request. */
@RestController
@RequestMapping("/crm/marketing/customer")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('crm:marketing:customer:edit')")
public class MktCustomerCreateController {
    private final CustomerMapper customers;
    private final MktPromoterMapper promoters;
    private final ProjectMapper projects;
    private final MktReferralService referrals;
    private final MktCompetitionProtectionService protection;
    private final MktCustomerAssignmentService assignment;
    private final MktAuditService audit;
    private final MktCustomerIntakeGuard intakeGuard;

    @GetMapping("/referrers")
    public Result<List<Map<String, Object>>> referrers(@RequestParam Long projectId,
            @RequestParam(defaultValue = "") String keyword) {
        requireProject(projectId);
        String search = keyword.trim();
        if (search.length() > 100) throw new BizException("搜索词最多 100 字");
        var rows = promoters.selectList(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getTenantId, MyMetaObjectHandler.DEFAULT_TENANT_ID)
                .eq(MktPromoter::getStatus, 1)
                .and(q -> q.eq(MktPromoter::getProjectId, projectId).or().isNull(MktPromoter::getProjectId))
                .and(!search.isEmpty(), q -> q.like(MktPromoter::getName, search).or().eq(MktPromoter::getInviteCode, search))
                .orderByDesc(MktPromoter::getId).last("LIMIT 50"));
        return Result.ok(rows.stream().map(p -> Map.<String, Object>of("id", p.getId(),
                "name", Objects.toString(p.getName(), ""), "inviteCode", Objects.toString(p.getInviteCode(), ""))).toList());
    }

    @PostMapping
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long projectId = id(body.get("projectId"), "请先选择园区");
        Project project = requireProject(projectId);
        Long partnerId = id(body.get("referrerId"), "推荐伙伴不正确");
        if (partnerId != null) {
            MktPromoter partner = promoters.selectForUpdate(partnerId);
            if (partner == null || !Integer.valueOf(1).equals(partner.getStatus())) throw new BizException("伙伴不存在或状态异常");
            if (partner.getProjectId() != null && !Objects.equals(projectId, partner.getProjectId())) throw new BizException("不能跨园区指定推荐伙伴");
            if (!Objects.equals(tenant(project.getTenantId()), tenant(partner.getTenantId()))) throw new BizException("伙伴不属于当前运营主体");
            Map<String, Object> result = referrals.createForProject(partnerId, projectId, body);
            audit.log("customer.admin.create", "customer", ((Number)result.get("customerId")).longValue(), "后台代伙伴建档");
            return Result.ok(result);
        }
        Customer customer = new Customer();
        String name = text(body, "name", 100), phone = text(body, "phone", 11);
        if (name == null) throw new BizException("客户名称必填");
        if (phone != null && !phone.matches("^1\\d{10}$")) throw new BizException("请填写正确的 11 位手机号，或留空");
        intakeGuard.lock(tenant(project.getTenantId()));
        if (customers.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getTenantId, tenant(project.getTenantId())).and(q -> {
            q.eq(Customer::getName, name);
            if (phone != null) q.or().eq(Customer::getPhone, phone);
        })) > 0) throw new BizException("该客户已在系统中，请使用已有客户档案");
        customer.setTenantId(tenant(project.getTenantId()));
        customer.setProjectId(projectId); customer.setName(name); customer.setPhone(phone);
        customer.setContact(text(body, "contact", 100)); customer.setBrandName(text(body, "brandName", 100));
        protection.assertUnprotected(customer.getTenantId(), name, customer.getBrandName());
        customer.setDemandArea(text(body, "demand", 200)); customer.setRemark(text(body, "remark", 1000));
        customer.setIntakeRemark(customer.getRemark());
        customer.setFulfillmentRequirements(MktReferralRequirements.normalize(body.get("requirements")));
        Long service = id(body.getOrDefault("serviceType", 2), "需求类型不正确");
        if (service == null || service < 1 || service > 4) throw new BizException("需求类型不正确");
        customer.setServiceType(service.intValue()); customer.setBizLine(service == 4 ? 1 : 2);
        Long warehouse = id(body.get("warehouseId"), "意向云仓不正确");
        if (warehouse != null) {
            if (service == 4) throw new BizException("园区入驻需求不选择云仓");
            assignment.requireAvailableWarehouse(warehouse, projectId);
            customer.setIntendedWarehouseId(warehouse);
        }
        customer.setStatus(1); customer.setOwner("待分配");
        customer.setWarehouseAssignmentStatus(MktCustomerAssignmentService.UNASSIGNED);
        customer.setAttributionNote("后台新增，园区直接客户");
        try { customers.insert(customer); }
        catch (DuplicateKeyException e) { throw new BizException("该客户已在系统中，请使用已有客户档案"); }
        audit.log("customer.admin.create", "customer", customer.getId(), "后台新增园区直接客户");
        return Result.ok(Map.of("customerId", customer.getId()));
    }

    private Project requireProject(Long id) {
        if (id == null) throw new BizException("请先选择园区");
        Project project = projects.selectById(id);
        if (project == null || !Objects.equals(tenant(project.getTenantId()), MyMetaObjectHandler.DEFAULT_TENANT_ID)) throw new BizException("园区不存在或无权访问");
        return project;
    }
    private static Long tenant(Long value) { return value == null ? MyMetaObjectHandler.DEFAULT_TENANT_ID : value; }
    private static Long id(Object value, String message) {
        if (value == null || value.toString().isBlank()) return null;
        try { long parsed = Long.parseLong(value.toString()); if (parsed > 0) return parsed; }
        catch (NumberFormatException ignored) { }
        throw new BizException(message);
    }
    private static String text(Map<String, Object> body, String key, int max) {
        Object raw = body.get(key);
        if (raw == null) return null;
        if (!(raw instanceof String)) throw new BizException("客户资料格式不正确");
        String value = ((String)raw).trim();
        if (value.length() > max) throw new BizException("客户资料超出长度限制");
        return value.isEmpty() ? null : value;
    }
}
