package com.zhyq.park.property.controller;

import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.PmInspector;
import com.zhyq.park.property.service.RoutineInspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 物业日常巡检/检查统一接口：
 * 设备巡检(EQUIPMENT) / 安防巡更(PATROL) / 保洁检查(CLEANING) / 绿化检查(GREENING)。
 */
@Tag(name = "物业-统一巡检与检查任务")
@RestController
@RequestMapping("/property/routine")
@RequiredArgsConstructor
public class RoutineInspectionController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final RoutineInspectionService service;

    @Operation(summary = "获取服务器当前时间(用于防篡改拍照水印)")
    @GetMapping("/server-time")
    public Result<Map<String, String>> serverTime() {
        return Result.ok(Map.of("now", LocalDateTime.now().format(TS)));
    }

    // ---- 巡检人管理 ----

    @Operation(summary = "巡检人列表")
    @GetMapping("/inspectors")
    public Result<List<PmInspector>> listInspectors(@RequestParam(required = false) String moduleType,
                                                    @RequestParam(required = false) Boolean onlyActive) {
        return Result.ok(service.listInspectors(moduleType, onlyActive));
    }

    @Operation(summary = "新增/修改巡检人")
    @PostMapping("/inspectors")
    public Result<PmInspector> saveInspector(@RequestBody PmInspector req) {
        return Result.ok(service.saveInspector(req));
    }

    @Operation(summary = "删除巡检人")
    @DeleteMapping("/inspectors/{id}")
    public Result<Void> deleteInspector(@PathVariable Long id) {
        service.deleteInspector(id);
        return Result.ok();
    }

    // ---- 巡检模板管理 ----

    @Operation(summary = "巡检模板列表(含检查项)")
    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> listTemplates(@RequestParam String moduleType) {
        return Result.ok(service.listTemplates(moduleType));
    }

    @Operation(summary = "新增/修改巡检模板及预设检查项")
    @PostMapping("/templates")
    public Result<Map<String, Object>> saveTemplate(@RequestBody RoutineInspectionService.TemplateSaveReq req) {
        return Result.ok(service.saveTemplate(req));
    }

    @Operation(summary = "删除巡检模板")
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        service.deleteTemplate(id);
        return Result.ok();
    }

    // ---- 每日/24小时巡检任务与现场拍照打卡 ----

    @Operation(summary = "查询某日巡检任务列表(当日自动按模板生成，未完成自动标红)")
    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> listTasks(
            @RequestParam String moduleType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long inspectorId) {
        return Result.ok(service.listTasks(moduleType, date, status, inspectorId));
    }

    @Operation(summary = "巡检任务详情")
    @GetMapping("/tasks/{taskId}")
    public Result<Map<String, Object>> getTask(@PathVariable Long taskId) {
        return Result.ok(service.getTaskDetail(taskId));
    }

    @Operation(summary = "检查项即时拍照打卡(必须上传带水印即时照片，全项拍完后任务自动完成)")
    @PostMapping("/tasks/{taskId}/items/{itemId}/capture")
    public Result<Map<String, Object>> captureItem(@PathVariable Long taskId,
                                                   @PathVariable Long itemId,
                                                   @RequestBody RoutineInspectionService.CaptureItemReq req) {
        return Result.ok(service.submitItemCapture(taskId, itemId, req));
    }

    @Operation(summary = "巡检发现异常一键转物业报修工单")
    @PostMapping("/items/{itemId}/to-workorder")
    public Result<Map<String, Object>> toWorkOrder(@PathVariable Long itemId) {
        return Result.ok(service.createWorkOrderFromItem(itemId));
    }

    // ---- 可视化分析统计 ----

    @Operation(summary = "巡检可视化汇总(日/月/年趋势、巡检人汇总、问题分析)")
    @GetMapping("/analytics")
    public Result<Map<String, Object>> analytics(
            @RequestParam String moduleType,
            @RequestParam(defaultValue = "MONTH") String granularity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(service.analytics(moduleType, granularity, date));
    }
}
