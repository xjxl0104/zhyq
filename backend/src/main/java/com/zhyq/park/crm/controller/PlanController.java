package com.zhyq.park.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Plan;
import com.zhyq.park.crm.mapper.PlanMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "招商-销售计划")
@RestController
@RequestMapping("/crm/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanMapper planMapper;

    @Operation(summary = "分页查询销售计划")
    @GetMapping("/page")
    public Result<PageResult<Plan>> page(@RequestParam(defaultValue = "1") int pageNo,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String title,
                                         @RequestParam(required = false) String period,
                                         @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Plan> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), Plan::getTitle, title)
          .eq(StringUtils.hasText(period), Plan::getPeriod, period)
          .eq(status != null, Plan::getStatus, status)
          .orderByDesc(Plan::getId);
        IPage<Plan> p = planMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "销售计划详情")
    @GetMapping("/{id}")
    public Result<Plan> get(@PathVariable Long id) {
        return Result.ok(planMapper.selectById(id));
    }

    @Operation(summary = "新增销售计划")
    @PostMapping
    public Result<Long> add(@RequestBody Plan plan) {
        planMapper.insert(plan);
        return Result.ok(plan.getId());
    }

    @Operation(summary = "修改销售计划")
    @PutMapping
    public Result<Void> update(@RequestBody Plan plan) {
        planMapper.updateById(plan);
        return Result.ok();
    }

    @Operation(summary = "删除销售计划")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        planMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "标记完成(进行中->已完成)")
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        LambdaUpdateWrapper<Plan> uw = new LambdaUpdateWrapper<>();
        uw.eq(Plan::getId, id).eq(Plan::getStatus, 1).set(Plan::getStatus, 2);
        if (planMapper.update(null, uw) == 0) {
            throw new BizException("仅进行中计划可标记完成");
        }
        return Result.ok();
    }
}
