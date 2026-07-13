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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "招商-意向客户")
@RestController
@RequestMapping("/crm/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerMapper customerMapper;
    private final LeadMapper leadMapper;

    @Operation(summary = "分页查询意向客户")
    @GetMapping("/page")
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
    public Result<Customer> get(@PathVariable Long id) {
        return Result.ok(customerMapper.selectById(id));
    }

    @Operation(summary = "新增意向客户")
    @PostMapping
    public Result<Long> add(@RequestBody Customer customer) {
        customerMapper.insert(customer);
        return Result.ok(customer.getId());
    }

    @Operation(summary = "修改意向客户")
    @PutMapping
    public Result<Void> update(@RequestBody Customer customer) {
        customerMapper.updateById(customer);
        return Result.ok();
    }

    @Operation(summary = "删除意向客户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "线索转意向客户")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/from-lead/{leadId}")
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
    @PostMapping("/{id}/sign")
    public Result<Void> sign(@PathVariable Long id) {
        LambdaUpdateWrapper<Customer> uw = new LambdaUpdateWrapper<>();
        uw.eq(Customer::getId, id).eq(Customer::getStatus, 1).set(Customer::getStatus, 2);
        if (customerMapper.update(null, uw) == 0) {
            throw new BizException("仅跟进中客户可签约");
        }
        return Result.ok();
    }

    @Operation(summary = "流失(跟进中->已流失)")
    @PostMapping("/{id}/lose")
    public Result<Void> lose(@PathVariable Long id) {
        LambdaUpdateWrapper<Customer> uw = new LambdaUpdateWrapper<>();
        uw.eq(Customer::getId, id).eq(Customer::getStatus, 1).set(Customer::getStatus, 3);
        if (customerMapper.update(null, uw) == 0) {
            throw new BizException("仅跟进中客户可标记流失");
        }
        return Result.ok();
    }
}
