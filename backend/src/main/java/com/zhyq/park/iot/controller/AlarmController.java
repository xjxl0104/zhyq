package com.zhyq.park.iot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.iot.entity.Alarm;
import com.zhyq.park.iot.entity.Device;
import com.zhyq.park.iot.mapper.AlarmMapper;
import com.zhyq.park.iot.mapper.DeviceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@Tag(name = "智慧物联-告警")
@RestController
@RequestMapping("/iot/alarm")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmMapper alarmMapper;
    private final DeviceMapper deviceMapper;
    private final ApplicationEventPublisher eventPublisher;

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

    @Operation(summary = "新增告警")
    @PostMapping
    public Result<Long> add(@RequestBody Alarm alarm) {
        alarmMapper.insert(alarm);
        try {
            Long spaceId = null;
            if (alarm.getDeviceId() != null) {
                Device device = deviceMapper.selectById(alarm.getDeviceId());
                if (device != null) {
                    spaceId = device.getSpaceId();
                }
            }
            eventPublisher.publishEvent(new DomainEvent.AlarmRaised(
                    alarm.getId(), alarm.getDeviceId(),
                    alarm.getLevel() == null ? null : String.valueOf(alarm.getLevel()),
                    spaceId, alarm.getAlarmType(), LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("发布 AlarmRaised 事件失败,不影响告警落库,alarmId={}", alarm.getId(), e);
        }
        return Result.ok(alarm.getId());
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
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm != null) {
            alarm.setStatus(2);
            alarmMapper.updateById(alarm);
        }
        return Result.ok();
    }

    @Operation(summary = "关闭告警(状态→5已关闭)")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm != null) {
            alarm.setStatus(5);
            alarmMapper.updateById(alarm);
        }
        return Result.ok();
    }
}
