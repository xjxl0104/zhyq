package com.zhyq.park.iot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.iot.entity.Alarm;
import com.zhyq.park.iot.mapper.AlarmMapper;
import com.zhyq.park.iot.service.AlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "智慧物联-告警")
@RestController
@RequestMapping("/iot/alarm")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmMapper alarmMapper;
    private final AlarmService alarmService;

    @Operation(summary = "分页查询告警")
    @GetMapping("/page")
    public Result<PageResult<Alarm>> page(@RequestParam(defaultValue = "1") int pageNo,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Integer level) {
        LambdaQueryWrapper<Alarm> qw = new LambdaQueryWrapper<>();
        qw.eq(status != null, Alarm::getStatus, status)
          .eq(level != null, Alarm::getLevel, level)
          .orderByDesc(Alarm::getId);
        IPage<Alarm> p = alarmMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "告警详情")
    @GetMapping("/{id}")
    public Result<Alarm> get(@PathVariable Long id) {
        return Result.ok(alarmMapper.selectById(id));
    }

    @Operation(summary = "新增告警(去重上报:同设备+同类型活动告警只累加次数,不重复建单)")
    @PostMapping
    public Result<Long> add(@RequestBody Alarm alarm) {
        return Result.ok(alarmService.raise(alarm));
    }

    @Operation(summary = "修改告警")
    @PutMapping
    public Result<Void> update(@RequestBody Alarm alarm) {
        alarmMapper.updateById(alarm);
        return Result.ok();
    }

    @Operation(summary = "删除告警")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        alarmMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "确认告警(状态→2已确认)")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        alarmService.confirm(id);
        return Result.ok();
    }

    @Operation(summary = "开始处理(状态→3处理中,可指派受理人)")
    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id, @RequestParam(required = false) String assignee) {
        alarmService.start(id, assignee);
        return Result.ok();
    }

    @Operation(summary = "标记恢复(状态→4已恢复,退出活动告警域)")
    @PostMapping("/{id}/recover")
    public Result<Void> recover(@PathVariable Long id) {
        alarmService.recover(id);
        return Result.ok();
    }

    @Operation(summary = "关闭告警(状态→5已关闭,退出活动告警域)")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        alarmService.close(id);
        return Result.ok();
    }

    @Operation(summary = "标记误报(状态→6误报,退出活动告警域)")
    @PostMapping("/{id}/false-positive")
    public Result<Void> falsePositive(@PathVariable Long id) {
        alarmService.falsePositive(id);
        return Result.ok();
    }

    @Operation(summary = "指派受理人")
    @PostMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @RequestParam String assignee) {
        alarmService.assign(id, assignee);
        return Result.ok();
    }
}
