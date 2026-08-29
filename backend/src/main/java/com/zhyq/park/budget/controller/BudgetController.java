package com.zhyq.park.budget.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.budget.entity.Budget;
import com.zhyq.park.budget.mapper.BudgetMapper;
import com.zhyq.park.budget.service.BudgetService;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 预算接口。权限口径对齐 ver6.6 采购模块的做法:每个接口都收口到方法级权限位,
 * 不靠 SecurityConfig 的 anyRequest().authenticated() 兜底。
 * 提交申请/归档/取消各自独立设点,不被 edit 覆盖 —— 能改草稿不等于能推进审批状态。
 * 权限点种子见 V42__budget_management.sql,命名与此处 @PreAuthorize 严格一致。
 */
@Tag(name = "预算管理-年度/月度预算")
@RestController
@RequestMapping("/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetMapper budgetMapper;
    private final BudgetService budgetService;

    @Operation(summary = "分页查询预算(budgetType:1年度 2月度)")
    @PreAuthorize("hasAuthority('budget:query')")
    @GetMapping("/page")
    public Result<PageResult<Budget>> page(@RequestParam(defaultValue = "1") int pageNo,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) Integer budgetType,
                                           @RequestParam(required = false) String period,
                                           @RequestParam(required = false) String title,
                                           @RequestParam(required = false) String department,
                                           @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Budget> qw = new LambdaQueryWrapper<>();
        qw.eq(budgetType != null, Budget::getBudgetType, budgetType)
          .like(StringUtils.hasText(period), Budget::getPeriod, period)
          .like(StringUtils.hasText(title), Budget::getTitle, title)
          .like(StringUtils.hasText(department), Budget::getDepartment, department)
          .eq(status != null, Budget::getStatus, status)
          .orderByDesc(Budget::getId);
        IPage<Budget> p = budgetMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "预算详情")
    @PreAuthorize("hasAuthority('budget:query')")
    @GetMapping("/{id}")
    public Result<Budget> get(@PathVariable Long id) {
        return Result.ok(budgetService.detail(id));
    }

    @Operation(summary = "新增预算(编号与状态由服务端生成,初始为草稿)")
    @PreAuthorize("hasAuthority('budget:add')")
    @PostMapping
    public Result<Long> add(@RequestBody Budget budget) {
        return Result.ok(budgetService.create(budget));
    }

    @Operation(summary = "编辑预算(仅草稿/已驳回可编辑,服务端按白名单字段更新)")
    @PreAuthorize("hasAuthority('budget:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody Budget budget) {
        budgetService.update(budget);
        return Result.ok();
    }

    @Operation(summary = "提交预算申请(草稿/已驳回→审批中,发起审批链)")
    @PreAuthorize("hasAuthority('budget:submit')")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        budgetService.submit(id);
        return Result.ok();
    }

    @Operation(summary = "归档预算(周期执行完毕)")
    @PreAuthorize("hasAuthority('budget:archive')")
    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        budgetService.archive(id);
        return Result.ok();
    }

    @Operation(summary = "取消预算")
    @PreAuthorize("hasAuthority('budget:cancel')")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        budgetService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "删除预算(审批中/已通过/已归档不可删)")
    @PreAuthorize("hasAuthority('budget:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        budgetService.remove(id);
        return Result.ok();
    }
}
