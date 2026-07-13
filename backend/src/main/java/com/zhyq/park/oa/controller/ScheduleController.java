package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.oa.entity.Schedule;
import com.zhyq.park.oa.mapper.ScheduleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "办公管理-日程")
@RestController
@RequestMapping("/oa/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleMapper scheduleMapper;

    @Operation(summary = "分页查询日程")
    @GetMapping("/page")
    public Result<PageResult<Schedule>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String title,
                                             @RequestParam(required = false) String stype,
                                             @RequestParam(required = false) String owner) {
        LambdaQueryWrapper<Schedule> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), Schedule::getTitle, title)
          .eq(StringUtils.hasText(stype), Schedule::getStype, stype)
          .eq(StringUtils.hasText(owner), Schedule::getOwner, owner)
          .orderByDesc(Schedule::getId);
        IPage<Schedule> p = scheduleMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "未来7天日程")
    @GetMapping("/week")
    public Result<List<Schedule>> week() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Schedule> qw = new LambdaQueryWrapper<>();
        qw.between(Schedule::getStartTime, now, now.plusDays(7))
          .orderByAsc(Schedule::getStartTime);
        return Result.ok(scheduleMapper.selectList(qw));
    }

    @Operation(summary = "日程详情")
    @GetMapping("/{id}")
    public Result<Schedule> get(@PathVariable Long id) {
        return Result.ok(scheduleMapper.selectById(id));
    }

    @Operation(summary = "新增日程")
    @PostMapping
    public Result<Long> add(@RequestBody Schedule schedule) {
        scheduleMapper.insert(schedule);
        return Result.ok(schedule.getId());
    }

    @Operation(summary = "修改日程")
    @PutMapping
    public Result<Void> update(@RequestBody Schedule schedule) {
        scheduleMapper.updateById(schedule);
        return Result.ok();
    }

    @Operation(summary = "删除日程")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleMapper.deleteById(id);
        return Result.ok();
    }
}
