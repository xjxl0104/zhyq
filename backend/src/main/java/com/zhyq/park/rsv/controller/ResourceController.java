package com.zhyq.park.rsv.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.rsv.entity.ResvResource;
import com.zhyq.park.rsv.mapper.ResvResourceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "资源预订-资源目录")
@RestController
@RequestMapping("/rsv/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final ResvResourceMapper resourceMapper;

    @Operation(summary = "分页查询资源")
    @GetMapping("/page")
    public Result<PageResult<ResvResource>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String name) {
        LambdaQueryWrapper<ResvResource> qw = new LambdaQueryWrapper<>();
        qw.eq(type != null, ResvResource::getType, type)
          .eq(status != null, ResvResource::getStatus, status)
          .like(name != null && !name.isEmpty(), ResvResource::getName, name)
          .orderByDesc(ResvResource::getId);
        IPage<ResvResource> p = resourceMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "资源详情")
    @GetMapping("/{id}")
    public Result<ResvResource> get(@PathVariable Long id) {
        return Result.ok(resourceMapper.selectById(id));
    }

    @Operation(summary = "新增资源")
    @PostMapping
    public Result<Long> add(@RequestBody ResvResource resource) {
        resourceMapper.insert(resource);
        return Result.ok(resource.getId());
    }

    @Operation(summary = "修改资源")
    @PutMapping
    public Result<Void> update(@RequestBody ResvResource resource) {
        resourceMapper.updateById(resource);
        return Result.ok();
    }

    @Operation(summary = "删除资源")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resourceMapper.deleteById(id);
        return Result.ok();
    }
}
