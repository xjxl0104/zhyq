package com.zhyq.park.iot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.iot.entity.Point;
import com.zhyq.park.iot.mapper.PointMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "智慧物联-点位")
@RestController
@RequestMapping("/iot/point")
@RequiredArgsConstructor
public class PointController {

    private final PointMapper pointMapper;

    @Operation(summary = "分页查询点位")
    @GetMapping("/page")
    public Result<PageResult<Point>> page(@RequestParam(defaultValue = "1") int pageNo,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Long projectId) {
        LambdaQueryWrapper<Point> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), Point::getName, name)
          .eq(status != null, Point::getStatus, status)
          .eq(projectId != null, Point::getProjectId, projectId)
          .orderByDesc(Point::getId);
        IPage<Point> p = pointMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "点位详情")
    @GetMapping("/{id}")
    public Result<Point> get(@PathVariable Long id) {
        return Result.ok(pointMapper.selectById(id));
    }

    @Operation(summary = "新增点位")
    @PostMapping
    public Result<Long> add(@RequestBody Point point) {
        pointMapper.insert(point);
        return Result.ok(point.getId());
    }

    @Operation(summary = "修改点位")
    @PutMapping
    public Result<Void> update(@RequestBody Point point) {
        pointMapper.updateById(point);
        return Result.ok();
    }

    @Operation(summary = "删除点位")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        pointMapper.deleteById(id);
        return Result.ok();
    }
}
