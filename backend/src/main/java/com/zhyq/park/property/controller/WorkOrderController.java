package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.property.service.WorkOrderLocationService;
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
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.zhyq.park.property.notify.WeComBotNotifier;
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
    private final BizTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final WorkOrderLocationService locations;
    private final WorkOrderLogMapper workOrderLogMapper;
    private final WorkOrderService workOrderService;
    private final SlaEscalationJob slaEscalationJob;
    private final WorkOrderSummaryService summaryService;
    private final ApplicationEventPublisher eventPublisher;
    private final WeComBotNotifier weComBotNotifier;

    public record AssigneeOption(Long id, String username, String nickname) {}

    @Operation(summary = "报修工单派单人员选项")
    @GetMapping("/assignees")
    public Result<List<AssigneeOption>> assignees() {
        return Result.ok(userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, 1).orderByAsc(SysUser::getId)).stream()
                .map(user -> new AssigneeOption(user.getId(), user.getUsername(), user.getNickname()))
                .toList());
    }

    @Operation(summary = "分页查询工单")
    @GetMapping("/page")
    public Result<PageResult<WorkOrder>> page(@RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(required = false) String orderType,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Integer urgency,
                                              @RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) Long buildingId,
                                              @RequestParam(required = false) Long floorId,
                                              @RequestParam(required = false) String zone,
                                              @RequestParam(required = false) Long tenantRefId,
                                              @RequestParam(required = false) Long id) {
        if (StringUtils.hasText(zone) && !List.of("A", "B", "C").contains(zone)) throw new BizException("无效的楼层分区");
        LambdaQueryWrapper<WorkOrder> qw = new LambdaQueryWrapper<>();
        qw.eq(id != null, WorkOrder::getId, id)
          .like(StringUtils.hasText(code), WorkOrder::getCode, code)
          .eq(StringUtils.hasText(orderType), WorkOrder::getOrderType, orderType)
          .eq(status != null, WorkOrder::getStatus, status)
          .eq(urgency != null, WorkOrder::getUrgency, urgency)
          .eq(projectId != null, WorkOrder::getProjectId, projectId)
          .eq(buildingId != null, WorkOrder::getBuildingId, buildingId)
          .eq(floorId != null, WorkOrder::getFloorId, floorId)
          .eq(StringUtils.hasText(zone), WorkOrder::getZone, zone)
          .eq(tenantRefId != null, WorkOrder::getTenantRefId, tenantRefId)
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
        if (wo != null) {
            m.put("building", locations.building(wo.getBuildingId()));
            m.put("floor", locations.floor(wo.getFloorId()));
        }
        m.put("logs", logs);
        return Result.ok(m);
    }

    @Operation(summary = "新增工单")
    @PostMapping
    public Result<Long> add(@RequestBody WorkOrder wo) {
        if (wo.getFloorLocation() != null) {
            var point = locations.resolve(wo.getFloorLocation(), wo.getProjectId(), MyMetaObjectHandler.DEFAULT_TENANT_ID);
            wo.setTenantId(MyMetaObjectHandler.DEFAULT_TENANT_ID);
            wo.setProjectId(point.projectId());
            wo.setBuildingId(point.buildingId());
            wo.setFloorId(point.floorId());
            wo.setZone(point.zone());
            wo.setFloorPlanFileId(point.planFileId());
            wo.setPlanX(point.x());
            wo.setPlanY(point.y());
            wo.setRoomId(null);
            wo.setSpaceId(null);
        }
        validateTenant(wo.getTenantRefId(), wo.getProjectId(), MyMetaObjectHandler.DEFAULT_TENANT_ID);
        if (!StringUtils.hasText(wo.getCode())) {
            wo.setCode("WO" + System.currentTimeMillis());
        }
        wo.setStatus(WorkOrderService.ST_PENDING_DISPATCH);
        wo.setFinishTime(null);
        wo.setResolutionCode(null);
        workOrderMapper.insert(wo);
        eventPublisher.publishEvent(new DomainEvent.WorkOrderCreated(
                wo.getId(), wo.getOrderType(), null, LocalDateTime.now()));
        return Result.ok(wo.getId());
    }

    @Operation(summary = "读取企业微信群机器人 Webhook")
    @GetMapping("/wecom-webhook")
    public Result<Map<String, Object>> getWeComWebhook() {
        Map<String, Object> m = new HashMap<>();
        m.put("webhook", weComBotNotifier.webhook());
        m.put("enabled", weComBotNotifier.enabled());
        return Result.ok(m);
    }

    @Operation(summary = "保存企业微信群机器人 Webhook(留空则关闭推送)")
    @PostMapping("/wecom-webhook")
    public Result<Void> saveWeComWebhook(@RequestBody Map<String, String> body) {
        weComBotNotifier.saveWebhook(body == null ? null : body.get("webhook"));
        return Result.ok();
    }

    @Operation(summary = "测试推送一条消息到企业微信群")
    @PostMapping("/wecom-webhook/test")
    public Result<Void> testWeComWebhook() {
        weComBotNotifier.sendTest("""
                **【测试消息】智慧园区工单通知已接通**
                > 之后新建报修工单会自动推送到本群""");
        return Result.ok();
    }

    @Operation(summary = "修改工单")
    @PutMapping
    public Result<Void> update(@RequestBody WorkOrder wo) {
        if (wo.getId() == null) throw new BizException("缺少工单编号");
        WorkOrder existing = workOrderMapper.selectById(wo.getId());
        if (existing == null) throw new BizException("工单不存在");
        if (wo.getStatus() != null && !wo.getStatus().equals(existing.getStatus()))
            throw new BizException("请通过工单流转操作修改状态，处理完成须上传现场照片");
        // 编辑基础资料不能写入流程状态或完成凭据，也不能用旧表单覆盖刚完成的状态。
        wo.setStatus(null);
        wo.setFinishTime(null);
        wo.setResolutionCode(null);
        boolean clearTenantRef = Boolean.TRUE.equals(wo.getClearTenantRef());
        if (clearTenantRef && wo.getTenantRefId() != null) throw new BizException("解除租客关联时不能同时选择租客");
        if (clearTenantRef) {
            wo.setTenantContact(null);
            wo.setTenantContactPhone(null);
        }
        if (wo.getTenantRefId() != null && !wo.getTenantRefId().equals(existing.getTenantRefId())) {
            validateTenant(wo.getTenantRefId(), existing.getProjectId(), existing.getTenantId());
        }
        if (wo.getFloorLocation() != null) {
            var point = locations.resolve(wo.getFloorLocation(), existing.getProjectId(), existing.getTenantId());
            // Explicit SET permits clearing a point without affecting partial status updates elsewhere.
            wo.setProjectId(null);
            wo.setBuildingId(null);
            wo.setRoomId(null);
            wo.setSpaceId(null);
            LambdaUpdateWrapper<WorkOrder> update = new LambdaUpdateWrapper<WorkOrder>().eq(WorkOrder::getId, wo.getId())
                    .set(WorkOrder::getProjectId, point.projectId()).set(WorkOrder::getBuildingId, point.buildingId())
                    .set(WorkOrder::getFloorId, point.floorId()).set(WorkOrder::getFloorPlanFileId, point.planFileId())
                    .set(WorkOrder::getZone, point.zone())
                    .set(WorkOrder::getPlanX, point.x()).set(WorkOrder::getPlanY, point.y())
                    .set(WorkOrder::getRoomId, null).set(WorkOrder::getSpaceId, null);
            if (clearTenantRef) update.set(WorkOrder::getTenantRefId, null)
                    .set(WorkOrder::getTenantContact, null).set(WorkOrder::getTenantContactPhone, null);
            workOrderMapper.update(wo, update);
        } else {
            if (existing.getFloorId() != null && ((wo.getBuildingId() != null && !wo.getBuildingId().equals(existing.getBuildingId()))
                    || (wo.getProjectId() != null && !wo.getProjectId().equals(existing.getProjectId()))
                    || (wo.getRoomId() != null && !wo.getRoomId().equals(existing.getRoomId())))) {
                throw new BizException("修改维修位置时请重新选择楼宇和楼层");
            }
            if (clearTenantRef) {
                workOrderMapper.update(wo, new LambdaUpdateWrapper<WorkOrder>().eq(WorkOrder::getId, wo.getId())
                        .set(WorkOrder::getTenantRefId, null)
                        .set(WorkOrder::getTenantContact, null).set(WorkOrder::getTenantContactPhone, null));
            } else {
                workOrderMapper.updateById(wo);
            }
        }
        return Result.ok();
    }

    private void validateTenant(Long tenantRefId, Long projectId, Long platformTenantId) {
        if (tenantRefId == null) return;
        BizTenant tenant = tenantMapper.selectById(tenantRefId);
        if (tenant == null || !java.util.Objects.equals(tenant.getTenantId(), platformTenantId)
                || !Integer.valueOf(1).equals(tenant.getStatus())) {
            throw new BizException("所选租客不存在或已归档");
        }
        if (projectId != null && tenant.getProjectId() != null && !projectId.equals(tenant.getProjectId())) {
            throw new BizException("租客不属于工单所在园区");
        }
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

    public record FinishRequest(String content, String resolutionCode, List<Long> photoIds) {}

    @Operation(summary = "处理完成（必须上传处理现场照片）")
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id, @RequestBody(required = false) FinishRequest body) {
        workOrderService.finish(id, MyMetaObjectHandler.currentOperator(),
                body == null ? null : body.content(), body == null ? null : body.resolutionCode(),
                body == null ? null : body.photoIds());
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
