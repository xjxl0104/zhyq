package com.zhyq.park.acc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.acc.entity.AccVisitor;
import com.zhyq.park.acc.mapper.AccVisitorMapper;
import com.zhyq.park.acc.service.AccVisitorService;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 便捷通行-访客预约/到访(#21)。状态机:1已预约 2已到访 3已离场 4已取消。
 */
@Tag(name = "便捷通行-访客")
@RestController
@RequestMapping("/acc/visitor")
@RequiredArgsConstructor
public class AccVisitorController {

    private final AccVisitorService visitorService;
    private final AccVisitorMapper visitorMapper;

    @Operation(summary = "分页查询访客")
    @GetMapping("/page")
    public Result<PageResult<AccVisitor>> page(@RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) String name,
                                               @RequestParam(required = false) String phone,
                                               @RequestParam(required = false) Long spaceId,
                                               @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<AccVisitor> qw = new LambdaQueryWrapper<>();
        qw.like(name != null && !name.isBlank(), AccVisitor::getName, name)
          .like(phone != null && !phone.isBlank(), AccVisitor::getPhone, phone)
          .eq(spaceId != null, AccVisitor::getSpaceId, spaceId)
          .eq(status != null, AccVisitor::getStatus, status)
          .orderByDesc(AccVisitor::getId);
        IPage<AccVisitor> p = visitorMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "访客详情")
    @GetMapping("/{id}")
    public Result<AccVisitor> get(@PathVariable Long id) {
        return Result.ok(visitorMapper.selectById(id));
    }

    @Operation(summary = "登记预约")
    @PostMapping("/reserve")
    public Result<Long> reserve(@RequestBody AccVisitor visitor) {
        return Result.ok(visitorService.reserve(visitor));
    }

    @Operation(summary = "到访签到(已预约→已到访)")
    @PostMapping("/{id}/checkin")
    public Result<Void> checkin(@PathVariable Long id) {
        visitorService.checkin(id);
        return Result.ok();
    }

    @Operation(summary = "离场(已到访→已离场)")
    @PostMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable Long id) {
        visitorService.leave(id);
        return Result.ok();
    }

    @Operation(summary = "取消预约(已预约→已取消)")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        visitorService.cancel(id);
        return Result.ok();
    }
}
