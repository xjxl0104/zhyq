package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.entity.WorkOrderLog;
import com.zhyq.park.property.mapper.WorkOrderLogMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.property.service.SlaEscalationJob;
import com.zhyq.park.property.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "物业-报修工单")
@RestController
@RequestMapping("/property/workorder")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final WorkOrderService workOrderService;
    private final SlaEscalationJob slaEscalationJob;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "分页查询工单")
    @GetMapping("/page")
    public Result<PageResult<WorkOrder>> page(@RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(required = false) String orderType,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Integer urgency,
                                              @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<WorkOrder> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(code), WorkOrder::getCode, code)
          .eq(StringUtils.hasText(orderType), WorkOrder::getOrderType, orderType)
          .eq(status != null, WorkOrder::getStatus, status)
          .eq(urgency != null, WorkOrder::getUrgency, urgency)
          .eq(projectId != null, WorkOrder::getProjectId, projectId)
          .orderByDesc(WorkOrder::getId);
        IPage<WorkOrder> p = workOrderMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "工单详情(含流转记录)")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        WorkOrder wo = workOrderMapper.selectById(id);
        List<WorkOrderLog> logs = workOrderLogMapper.selectList(
                new LambdaQueryWrapper<WorkOrderLog>()
                        .eq(WorkOrderLog::getOrderId, id)
                        .orderByAsc(WorkOrderLog::getId));
        Map<String, Object> m = new HashMap<>();
        m.put("order", wo);
        m.put("logs", logs);
        return Result.ok(m);
    }

    @Operation(summary = "新增工单")
    @PostMapping
    public Result<Long> add(@RequestBody WorkOrder wo) {
        if (!StringUtils.hasText(wo.getCode())) {
            wo.setCode("WO" + System.currentTimeMillis());
        }
        if (wo.getStatus() == null) {
            wo.setStatus(WorkOrderService.ST_PENDING_DISPATCH);
        }
        workOrderMapper.insert(wo);
        eventPublisher.publishEvent(new DomainEvent.WorkOrderCreated(
                wo.getId(), wo.getOrderType(), null, LocalDateTime.now()));
        return Result.ok(wo.getId());
    }

    @Operation(summary = "修改工单")
    @PutMapping
    public Result<Void> update(@RequestBody WorkOrder wo) {
        workOrderMapper.updateById(wo);
        return Result.ok();
    }

    @Operation(summary = "删除工单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workOrderMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "派单")
    @PostMapping("/{id}/dispatch")
    public Result<Void> dispatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String assignee = strOf(body.get("assignee"));
        workOrderService.dispatch(id, assignee);
        return Result.ok();
    }

    @Operation(summary = "接单")
    @PostMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        workOrderService.accept(id, operatorOf(body));
        return Result.ok();
    }

    @Operation(summary = "到场")
    @PostMapping("/{id}/arrive")
    public Result<Void> arrive(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        workOrderService.arrive(id, operatorOf(body));
        return Result.ok();
    }

    @Operation(summary = "处理完成")
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String content = body == null ? null : strOf(body.get("content"));
        String resolutionCode = body == null ? null : strOf(body.get("resolutionCode"));
        workOrderService.finish(id, operatorOf(body), content, resolutionCode);
        return Result.ok();
    }

    @Operation(summary = "回访评价")
    @PostMapping("/{id}/revisit")
    public Result<Void> revisit(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Integer score = null;
        if (body != null && body.get("score") != null) {
            score = Integer.valueOf(String.valueOf(body.get("score")));
        }
        String remark = body == null ? null : strOf(body.get("remark"));
        workOrderService.revisit(id, operatorOf(body), score, remark);
        return Result.ok();
    }

    @Operation(summary = "验收")
    @PostMapping("/{id}/verify")
    public Result<Void> verify(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Integer score = null;
        if (body != null && body.get("score") != null) {
            score = Integer.valueOf(String.valueOf(body.get("score")));
        }
        workOrderService.verify(id, operatorOf(body), score);
        return Result.ok();
    }

    @Operation(summary = "关闭")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        workOrderService.close(id, operatorOf(body), body == null ? null : strOf(body.get("content")));
        return Result.ok();
    }

    @Operation(summary = "手动触发SLA超时扫描(调试/运维工具,复用定时任务同一逻辑)")
    @PostMapping("/sla-scan")
    public Result<Void> slaScan() {
        slaEscalationJob.doScan();
        return Result.ok();
    }

    @Operation(summary = "工单统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long pending = workOrderMapper.selectCount(
                new LambdaQueryWrapper<WorkOrder>().eq(WorkOrder::getStatus, WorkOrderService.ST_PENDING_DISPATCH));
        long processing = workOrderMapper.selectCount(
                new LambdaQueryWrapper<WorkOrder>().eq(WorkOrder::getStatus, WorkOrderService.ST_PROCESSING));
        long done = workOrderMapper.selectCount(
                new LambdaQueryWrapper<WorkOrder>().eq(WorkOrder::getStatus, WorkOrderService.ST_DONE));
        long total = workOrderMapper.selectCount(new LambdaQueryWrapper<>());
        Map<String, Object> m = new HashMap<>();
        m.put("pending", pending);
        m.put("processing", processing);
        m.put("done", done);
        m.put("total", total);
        return Result.ok(m);
    }

    private static String operatorOf(Map<String, Object> body) {
        return body == null ? null : strOf(body.get("operator"));
    }

    private static String strOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
