package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.InspectionPlan;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.InspectionPlanMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.property.model.WorkOrderSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 巡检计划
 * 状态:1启用 0停用
 */
@Tag(name = "物业-巡检计划")
@RestController
@RequestMapping("/property/inspection")
@RequiredArgsConstructor
public class InspectionPlanController {

    private final InspectionPlanMapper inspectionPlanMapper;
    private final WorkOrderMapper workOrderMapper;

    @Operation(summary = "分页查询巡检计划")
    @GetMapping("/page")
    public Result<PageResult<InspectionPlan>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                   @RequestParam(defaultValue = "10") int pageSize,
                                                   @RequestParam(required = false) String name,
                                                   @RequestParam(required = false) String cycle,
                                                   @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<InspectionPlan> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), InspectionPlan::getName, name)
          .eq(StringUtils.hasText(cycle), InspectionPlan::getCycle, cycle)
          .eq(status != null, InspectionPlan::getStatus, status)
          .orderByDesc(InspectionPlan::getId);
        IPage<InspectionPlan> p = inspectionPlanMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "巡检计划详情")
    @GetMapping("/{id}")
    public Result<InspectionPlan> get(@PathVariable Long id) {
        return Result.ok(inspectionPlanMapper.selectById(id));
    }

    @Operation(summary = "新增巡检计划")
    @PostMapping
    public Result<Long> add(@RequestBody InspectionPlan plan) {
        if (plan.getStatus() == null) {
            plan.setStatus(1);
        }
        inspectionPlanMapper.insert(plan);
        return Result.ok(plan.getId());
    }

    @Operation(summary = "修改巡检计划")
    @PutMapping
    public Result<Void> update(@RequestBody InspectionPlan plan) {
        inspectionPlanMapper.updateById(plan);
        return Result.ok();
    }

    @Operation(summary = "删除巡检计划")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        inspectionPlanMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "按计划生成巡检工单")
    @PostMapping("/{id}/generate")
    public Result<Map<String, Object>> generate(@PathVariable Long id) {
        InspectionPlan plan = inspectionPlanMapper.selectById(id);
        if (plan == null) {
            throw new BizException("巡检计划不存在");
        }
        WorkOrder wo = new WorkOrder();
        wo.setCode("GD" + System.currentTimeMillis());
        wo.setOrderType("巡检");
        wo.setTitle(plan.getName() + " - 巡检任务");
        wo.setProjectId(plan.getProjectId());
        wo.setCategory("巡检");
        wo.setUrgency(2);
        wo.setStatus(1);
        wo.setSource("巡检计划");
        // 写来源主键,否则工单反查不到是哪个巡检计划生成的
        wo.setSourceType(WorkOrderSource.INSPECTION_PLAN);
        wo.setSourceId(plan.getId());
        workOrderMapper.insert(wo);
        Map<String, Object> m = new HashMap<>();
        m.put("id", wo.getId());
        m.put("code", wo.getCode());
        return Result.ok(m);
    }
}
