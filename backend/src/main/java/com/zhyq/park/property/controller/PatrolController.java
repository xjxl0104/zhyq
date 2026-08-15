package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.Patrol;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.PatrolMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.property.model.WorkOrderSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "物业-安防巡更")
@RestController
@RequestMapping("/property/patrol")
@RequiredArgsConstructor
public class PatrolController {

    private final PatrolMapper patrolMapper;
    private final WorkOrderMapper workOrderMapper;

    @Operation(summary = "分页查询巡更记录")
    @GetMapping("/page")
    public Result<PageResult<Patrol>> page(@RequestParam(defaultValue = "1") int pageNo,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String routeName,
                                           @RequestParam(required = false) String result,
                                           @RequestParam(required = false) String patroller) {
        LambdaQueryWrapper<Patrol> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(routeName), Patrol::getRouteName, routeName)
          .eq(StringUtils.hasText(result), Patrol::getResult, result)
          .like(StringUtils.hasText(patroller), Patrol::getPatroller, patroller)
          .orderByDesc(Patrol::getId);
        IPage<Patrol> p = patrolMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "巡更记录详情")
    @GetMapping("/{id}")
    public Result<Patrol> get(@PathVariable Long id) {
        return Result.ok(patrolMapper.selectById(id));
    }

    @Operation(summary = "新增巡更记录")
    @PostMapping
    public Result<Long> add(@RequestBody Patrol patrol) {
        patrolMapper.insert(patrol);
        return Result.ok(patrol.getId());
    }

    @Operation(summary = "修改巡更记录")
    @PutMapping
    public Result<Void> update(@RequestBody Patrol patrol) {
        patrolMapper.updateById(patrol);
        return Result.ok();
    }

    @Operation(summary = "删除巡更记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        patrolMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "巡更统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long total = patrolMapper.selectCount(new LambdaQueryWrapper<>());
        long normal = patrolMapper.selectCount(new LambdaQueryWrapper<Patrol>().eq(Patrol::getResult, "正常"));
        long abnormal = patrolMapper.selectCount(new LambdaQueryWrapper<Patrol>().eq(Patrol::getResult, "异常"));
        Map<String, Object> m = new HashMap<>();
        m.put("total", total);
        m.put("normal", normal);
        m.put("abnormal", abnormal);
        return Result.ok(m);
    }

    @Operation(summary = "异常巡更转工单")
    @PostMapping("/{id}/toWorkOrder")
    public Result<String> toWorkOrder(@PathVariable Long id) {
        Patrol patrol = patrolMapper.selectById(id);
        if (patrol == null) {
            throw new BizException("巡更记录不存在");
        }
        if (!"异常".equals(patrol.getResult())) {
            throw new BizException("仅异常巡更记录可转工单");
        }
        WorkOrder wo = new WorkOrder();
        wo.setCode("GD" + System.currentTimeMillis());
        wo.setOrderType("巡检");
        wo.setTitle("巡更异常:" + patrol.getPoint());
        wo.setLocation(patrol.getPoint());
        wo.setCategory("安防");
        wo.setUrgency(3);
        wo.setStatus(1);
        wo.setSource("安防巡更");
        // 写来源主键,否则工单反查不到是哪条巡更记录生成的
        wo.setSourceType(WorkOrderSource.PATROL);
        wo.setSourceId(patrol.getId());
        workOrderMapper.insert(wo);
        return Result.ok(wo.getCode());
    }
}
