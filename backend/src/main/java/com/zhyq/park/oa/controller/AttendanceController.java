package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.oa.entity.Attendance;
import com.zhyq.park.oa.mapper.AttendanceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 考勤管理(oa_attendance,规格书:首期只同步不自研排班,本页仅展示与人工补录)
 */
@Tag(name = "办公管理-考勤")
@RestController
@RequestMapping("/oa/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceMapper attendanceMapper;

    @Operation(summary = "分页查询考勤")
    @GetMapping("/page")
    public Result<PageResult<Attendance>> page(@RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) String userName,
                                               @RequestParam(required = false) String attStatus,
                                               @RequestParam(required = false) LocalDate attDate) {
        LambdaQueryWrapper<Attendance> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(userName), Attendance::getUserName, userName)
          .eq(StringUtils.hasText(attStatus), Attendance::getAttStatus, attStatus)
          .eq(attDate != null, Attendance::getAttDate, attDate)
          .orderByDesc(Attendance::getAttDate)
          .orderByDesc(Attendance::getId);
        IPage<Attendance> p = attendanceMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "当日考勤统计:各状态人数")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats(@RequestParam(required = false) LocalDate attDate) {
        LocalDate date = attDate != null ? attDate : LocalDate.now();
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("normal", attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getAttDate, date).eq(Attendance::getAttStatus, "正常")));
        map.put("late", attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getAttDate, date).eq(Attendance::getAttStatus, "迟到")));
        map.put("early", attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getAttDate, date).eq(Attendance::getAttStatus, "早退")));
        map.put("absent", attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getAttDate, date).eq(Attendance::getAttStatus, "缺勤")));
        map.put("out", attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getAttDate, date).eq(Attendance::getAttStatus, "外勤")));
        return Result.ok(map);
    }

    @Operation(summary = "考勤详情")
    @GetMapping("/{id}")
    public Result<Attendance> get(@PathVariable Long id) {
        return Result.ok(attendanceMapper.selectById(id));
    }

    @Operation(summary = "新增考勤(人工补录)")
    @PostMapping
    public Result<Long> add(@RequestBody Attendance attendance) {
        attendanceMapper.insert(attendance);
        return Result.ok(attendance.getId());
    }

    @Operation(summary = "修改考勤")
    @PutMapping
    public Result<Void> update(@RequestBody Attendance attendance) {
        attendanceMapper.updateById(attendance);
        return Result.ok();
    }

    @Operation(summary = "删除考勤")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        attendanceMapper.deleteById(id);
        return Result.ok();
    }
}
