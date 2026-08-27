package com.zhyq.park.pur.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.pur.entity.PurPlan;
import com.zhyq.park.pur.mapper.PurPlanMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "采购管理-采购计划")
@RestController
@RequestMapping("/pur/plan")
@RequiredArgsConstructor
public class PurPlanController {

    private final PurPlanMapper planMapper;

    @Operation(summary = "分页查询采购计划(planType:1年度 2月度 3临时)")
    @GetMapping("/page")
    public Result<PageResult<PurPlan>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Integer planType,
                                            @RequestParam(required = false) String period,
                                            @RequestParam(required = false) String title,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<PurPlan> qw = new LambdaQueryWrapper<>();
        qw.eq(planType != null, PurPlan::getPlanType, planType)
          .like(StringUtils.hasText(period), PurPlan::getPeriod, period)
          .like(StringUtils.hasText(title), PurPlan::getTitle, title)
          .eq(status != null, PurPlan::getStatus, status)
          .orderByDesc(PurPlan::getId);
        IPage<PurPlan> p = planMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "采购计划下拉列表(供采购申请关联)")
    @GetMapping("/list")
    public Result<List<PurPlan>> list(@RequestParam(required = false) Integer planType) {
        return Result.ok(planMapper.selectList(new LambdaQueryWrapper<PurPlan>()
                .eq(planType != null, PurPlan::getPlanType, planType)
                .orderByDesc(PurPlan::getId)));
    }

    @Operation(summary = "采购计划详情")
    @GetMapping("/{id}")
    public Result<PurPlan> get(@PathVariable Long id) {
        return Result.ok(planMapper.selectById(id));
    }

    @Operation(summary = "新增采购计划")
    @PostMapping
    public Result<Long> add(@RequestBody PurPlan plan) {
        plan.setId(null);
        if (plan.getStatus() == null) {
            plan.setStatus(1);
        }
        planMapper.insert(plan);
        return Result.ok(plan.getId());
    }

    @Operation(summary = "编辑采购计划")
    @PutMapping
    public Result<Void> update(@RequestBody PurPlan plan) {
        planMapper.updateById(plan);
        return Result.ok();
    }

    @Operation(summary = "删除采购计划")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        planMapper.deleteById(id);
        return Result.ok();
    }
}
