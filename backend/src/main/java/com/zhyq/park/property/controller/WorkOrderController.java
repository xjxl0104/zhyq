package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.model.WorkOrderSource;
import com.zhyq.park.property.entity.WorkOrderLog;
import com.zhyq.park.property.mapper.WorkOrderLogMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.property.service.SlaEscalationJob;
import com.zhyq.park.property.service.WorkOrderService;
import com.zhyq.park.property.service.WorkOrderSummaryService;
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
import java.util.stream.Collectors;

@Tag(name = "物业-报修工单")
@RestController
@RequestMapping("/property/workorder")
@RequiredArgsConstructor
public class WorkOrderController {

    /**
     * count-by-source 单次 IN 的上限, 与前端最大页长(50)对齐并留余量。
     * 前端页长若调大(如做导出), 这里要跟着改。
     */
    private static final int MAX_COUNT_BY_SOURCE_IDS = 100;

    /** 单条源记录反查工单的返回上限, 防脏数据把整表拉进内存 */
    private static final int MAX_ORDERS_PER_SOURCE = 500;

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final WorkOrderService workOrderService;
    private final SlaEscalationJob slaEscalationJob;
    private final WorkOrderSummaryService summaryService;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "分页查询工单")
    @GetMapping("/page")
    public Result<PageResult<WorkOrder>> page(@RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(required = false) String orderType,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Integer urgency,
                                              @RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) Long id) {
        LambdaQueryWrapper<WorkOrder> qw = new LambdaQueryWrapper<>();
        qw.eq(id != null, WorkOrder::getId, id)
          .like(StringUtils.hasText(code), WorkOrder::getCode, code)
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

    @Operation(summary = "按来源反查工单列表(源记录 → 派生工单)")
    @GetMapping("/by-source")
    public Result<List<WorkOrder>> bySource(@RequestParam String sourceType,
                                            @RequestParam Long sourceId,
                                            @RequestParam(required = false) Long projectId) {
        if (!WorkOrderSource.isQueryable(sourceType)) {
            throw new BizException("不支持的来源类型:" + sourceType);
        }
        List<WorkOrder> list = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .eq(WorkOrder::getSourceType, sourceType)
                        .eq(WorkOrder::getSourceId, sourceId)
                        // 与本文件 page() 保持一致的项目隔离口径。
                        // projectId 由前端拦截器自动注入(见 utils/request.js)
                        .eq(projectId != null, WorkOrder::getProjectId, projectId)
                        .orderByDesc(WorkOrder::getId)
                        // 一条源记录理论上可反复转单, 没有业务硬上限。
                        // 正常个位数, 加个兜底防脏数据(重复点击/脚本)把整表拉进内存。
                        .last("LIMIT " + MAX_ORDERS_PER_SOURCE));
        return Result.ok(list);
    }

    /**
     * 源页面列表要在每行显示"已派生 N 个工单", 逐行请求会 N+1。
     * 这里一次查一页的 id 集合, 返回 sourceId → 工单数。
     */
    @Operation(summary = "按来源批量统计工单数(供源记录列表显示徽标)")
    @GetMapping("/count-by-source")
    public Result<Map<Long, Long>> countBySource(@RequestParam String sourceType,
                                                  // 前端逗号拼接传入(sourceIds=1,2,3), 靠 Spring 的
                                                  // StringToCollection 转换器绑定。不能让 axios 按数组序列化
                                                  // (会变成 sourceIds[]=1&sourceIds[]=2, 这边绑不上)。
                                                  // 改前端序列化方式时这里会一起坏, 见 api/property.js 对应注释。
                                                  @RequestParam List<Long> sourceIds,
                                                  @RequestParam(required = false) Long projectId) {
        if (!WorkOrderSource.isQueryable(sourceType)) {
            throw new BizException("不支持的来源类型:" + sourceType);
        }
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Result.ok(Map.of());
        }
        // 上限兜底:sourceIds 来自前端一页的行数, 正常 ≤50。
        // 不限长的话一个超长 IN 会打挂 SQL。
        if (sourceIds.size() > MAX_COUNT_BY_SOURCE_IDS) {
            throw new BizException("单次最多查询 " + MAX_COUNT_BY_SOURCE_IDS + " 条来源记录");
        }
        List<WorkOrder> list = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .select(WorkOrder::getSourceId)
                        .eq(WorkOrder::getSourceType, sourceType)
                        .in(WorkOrder::getSourceId, sourceIds)
                        // 与 bySource/page 同口径, 否则徽标数会把别的项目的工单算进来
                        .eq(projectId != null, WorkOrder::getProjectId, projectId));
        Map<Long, Long> counts = list.stream()
                .collect(Collectors.groupingBy(WorkOrder::getSourceId, Collectors.counting()));
        return Result.ok(counts);
    }

    @Operation(summary = "手动触发SLA超时扫描(调试/运维工具,复用定时任务同一逻辑)")
    @PostMapping("/sla-scan")
    public Result<Void> slaScan() {
        slaEscalationJob.doScan();
        return Result.ok();
    }

    @Operation(summary = "工单统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Long projectId) {
        // 原实现只统计状态 1/3/5, 漏了 2/4/6/7, 三个数之和不等于 total。
        // 改为一次查出按状态分组的全量计数, 保证各口径可加总。
        Map<Integer, Long> byStatus = summaryService.countByStatus(projectId);
        return Result.ok(summaryService.toStatsMap(byStatus));
    }

    @Operation(summary = "工单汇总总览:按状态/来源/分类/紧急度聚合 + SLA 达成率")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) Integer days) {
        return Result.ok(summaryService.summary(projectId, days == null ? 30 : days));
    }

    private static String operatorOf(Map<String, Object> body) {
        return body == null ? null : strOf(body.get("operator"));
    }

    private static String strOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
