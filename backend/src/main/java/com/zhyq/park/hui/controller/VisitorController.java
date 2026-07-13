package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.Visitor;
import com.zhyq.park.hui.mapper.VisitorMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "惠企服务-访客预约")
@RestController
@RequestMapping("/service/visitor")
@RequiredArgsConstructor
public class VisitorController {

    private final VisitorMapper visitorMapper;

    @Operation(summary = "分页查询访客")
    @GetMapping("/page")
    public Result<PageResult<Visitor>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Visitor> qw = new LambdaQueryWrapper<>();
        qw.eq(status != null, Visitor::getStatus, status)
          .orderByDesc(Visitor::getId);
        IPage<Visitor> p = visitorMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "访客详情")
    @GetMapping("/{id}")
    public Result<Visitor> get(@PathVariable Long id) {
        return Result.ok(visitorMapper.selectById(id));
    }

    @Operation(summary = "新增访客")
    @PostMapping
    public Result<Long> add(@RequestBody Visitor visitor) {
        visitorMapper.insert(visitor);
        return Result.ok(visitor.getId());
    }

    @Operation(summary = "修改访客")
    @PutMapping
    public Result<Void> update(@RequestBody Visitor visitor) {
        visitorMapper.updateById(visitor);
        return Result.ok();
    }

    @Operation(summary = "删除访客")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        visitorMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "审批通过(状态→2已通过)")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        Visitor visitor = visitorMapper.selectById(id);
        if (visitor != null) {
            visitor.setStatus(2);
            visitorMapper.updateById(visitor);
        }
        return Result.ok();
    }
}
