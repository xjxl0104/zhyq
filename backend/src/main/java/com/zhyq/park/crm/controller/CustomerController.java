package com.zhyq.park.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.entity.Lead;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.crm.mapper.LeadMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "招商-意向客户")
@RestController
@RequestMapping("/crm/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerMapper customerMapper;
    private final LeadMapper leadMapper;
    private final com.zhyq.park.marketing.service.MktCustomerAssignmentService assignmentService;
    private final com.zhyq.park.marketing.service.MktLockService marketingLocks;
    private final com.zhyq.park.marketing.service.MktCustomerDeletionService deletionService;

    @Operation(summary = "分页查询意向客户")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('crm:customer:query')")
    public Result<PageResult<Customer>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String name,
                                             @RequestParam(required = false) String intentLevel,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(required = false) String owner) {
        LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), Customer::getName, name)
          .eq(StringUtils.hasText(intentLevel), Customer::getIntentLevel, intentLevel)
          .eq(status != null, Customer::getStatus, status)
          .eq(StringUtils.hasText(owner), Customer::getOwner, owner)
          .orderByDesc(Customer::getId);
        IPage<Customer> p = customerMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "意向客户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:customer:query')")
    public Result<Customer> get(@PathVariable Long id) {
        return Result.ok(customerMapper.selectById(id));
    }

    @Operation(summary = "新增意向客户")
    @PostMapping
    @PreAuthorize("hasAuthority('crm:customer:add')")
    public Result<Long> add(@RequestBody Customer customer) {
        if (customer.getReferrerId() != null || customer.getGrade() != null)
            throw new BizException("请先建档，再到全民营销设置推荐伙伴及评级");
        customerMapper.insert(customer);
        return Result.ok(customer.getId());
    }

    @Operation(summary = "修改意向客户")
    @PutMapping
    @Transactional
    @PreAuthorize("hasAuthority('crm:customer:edit')")
    public Result<Void> update(@RequestBody Customer customer) {
        Customer current = requireLocked(customer.getId());
        if ((customer.getReferrerId() != null && !java.util.Objects.equals(customer.getReferrerId(), current.getReferrerId()))
                || (customer.getGrade() != null && !java.util.Objects.equals(customer.getGrade(), current.getGrade()))
                || (customer.getSignMode() != null && !java.util.Objects.equals(customer.getSignMode(), current.getSignMode())))
            throw new BizException("请在全民营销客户管理中调整归属、评级或签约方式");
        if (isMarketing(current) && ((customer.getStatus() != null && !java.util.Objects.equals(customer.getStatus(), current.getStatus()))
                || (customer.getServiceType() != null && !java.util.Objects.equals(customer.getServiceType(), current.getServiceType()))
                || (customer.getProjectId() != null && !java.util.Objects.equals(customer.getProjectId(), current.getProjectId()))))
            throw new BizException("营销客户状态由合同和归属流程维护，请到全民营销处理");
        customer.setVersion(current.getVersion());
        if (customerMapper.updateById(customer) != 1) throw new BizException("客户已变化，请刷新后重试");
        return Result.ok();
    }

    @Operation(summary = "删除意向客户")
    @Transactional
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:customer:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        Customer current = requireLocked(id);
        assignmentService.assertNoActiveContracts(id);
        if (isMarketing(current) || marketingLocks.displayLockOf(id) != null)
            throw new BizException("该客户关联营销业务，请保留档案并按业务流程标记流失");
        deletionService.deleteOrdinary(id);
        return Result.ok();
    }

    @Operation(summary = "线索转意向客户")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/from-lead/{leadId}")
    @PreAuthorize("hasAuthority('crm:customer:add') and hasAuthority('crm:lead:edit')")
    public Result<Long> fromLead(@PathVariable Long leadId) {
        Lead lead = leadMapper.selectById(leadId);
        if (lead == null) {
            throw new BizException("线索不存在");
        }
        // 先条件更新线索状态:仅 in(1,2,3,4) 可转化 -> 5 已转化
        LambdaUpdateWrapper<Lead> uw = new LambdaUpdateWrapper<>();
        uw.in(Lead::getStatus, 1, 2, 3, 4)
          .eq(Lead::getId, leadId)
          .set(Lead::getStatus, 5);
        int updated = leadMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("该线索已转化或不可转化");
        }
        // 再插入意向客户
        Customer c = new Customer();
        c.setName(StringUtils.hasText(lead.getCompany()) ? lead.getCompany() : lead.getContact());
        c.setContact(lead.getContact());
        c.setPhone(lead.getPhone());
        c.setDemandArea(lead.getDemandArea());
        c.setSourceLeadId(leadId);
        c.setOwner("招商小李");
        c.setIntentLevel("B");
        c.setStatus(1);
        customerMapper.insert(c);
        return Result.ok(c.getId());
    }

    @Operation(summary = "签约(跟进中->已签约)")
    @Transactional
    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAuthority('crm:customer:edit')")
    public Result<Void> sign(@PathVariable Long id) {
        if (isMarketing(requireLocked(id))) throw new BizException("营销客户请通过真实合同签署生效，不支持直接改为已签约");
        LambdaUpdateWrapper<Customer> uw = new LambdaUpdateWrapper<>();
        uw.eq(Customer::getId, id).eq(Customer::getStatus, 1).set(Customer::getStatus, 2);
        if (customerMapper.update(null, uw) == 0) {
            throw new BizException("仅跟进中客户可签约");
        }
        return Result.ok();
    }

    @Operation(summary = "流失(跟进中->已流失)")
    @Transactional
    @PostMapping("/{id}/lose")
    @PreAuthorize("hasAuthority('crm:customer:edit')")
    public Result<Void> lose(@PathVariable Long id) {
        if (isMarketing(requireLocked(id))) throw new BizException("请在全民营销中标记流失并填写原因");
        assignmentService.assertNoActiveContracts(id);
        LambdaUpdateWrapper<Customer> uw = new LambdaUpdateWrapper<>();
        uw.eq(Customer::getId, id).eq(Customer::getStatus, 1).set(Customer::getStatus, 3);
        if (customerMapper.update(null, uw) == 0) {
            throw new BizException("仅跟进中客户可标记流失");
        }
        return Result.ok();
    }
    private Customer requireLocked(Long id) {
        Customer c = id == null ? null : customerMapper.selectForUpdate(id);
        if (c == null) throw new BizException("客户不存在");
        return c;
    }
    private static boolean isMarketing(Customer c) {
        return c.getReferrerId() != null || c.getAssignedWarehouseId() != null;
    }
}
