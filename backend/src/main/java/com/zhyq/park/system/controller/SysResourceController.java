package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysResource;
import com.zhyq.park.system.mapper.SysResourceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统管理-运营资源")
@RestController
@RequestMapping("/system/resource")
@RequiredArgsConstructor
public class SysResourceController {

    private final SysResourceMapper resourceMapper;

    @Operation(summary = "分页查询运营资源")
    @GetMapping("/page")
    public Result<PageResult<SysResource>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String rtype,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String title) {
        LambdaQueryWrapper<SysResource> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(rtype), SysResource::getRtype, rtype)
          .eq(status != null, SysResource::getStatus, status)
          .like(StringUtils.hasText(title), SysResource::getTitle, title)
          .orderByAsc(SysResource::getSort)
          .orderByDesc(SysResource::getId);
        IPage<SysResource> p = resourceMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "运营资源详情")
    @GetMapping("/{id}")
    public Result<SysResource> get(@PathVariable Long id) {
        return Result.ok(resourceMapper.selectById(id));
    }

    @Operation(summary = "新增运营资源")
    @PostMapping
    public Result<Long> add(@RequestBody SysResource resource) {
        resourceMapper.insert(resource);
        return Result.ok(resource.getId());
    }

    @Operation(summary = "修改运营资源")
    @PutMapping
    public Result<Void> update(@RequestBody SysResource resource) {
        resourceMapper.updateById(resource);
        return Result.ok();
    }

    @Operation(summary = "删除运营资源")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resourceMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "上下架切换")
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        SysResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BizException("运营资源不存在");
        }
        int next = resource.getStatus() != null && resource.getStatus() == 1 ? 0 : 1;
        resourceMapper.update(null, new LambdaUpdateWrapper<SysResource>()
                .eq(SysResource::getId, id)
                .set(SysResource::getStatus, next));
        return Result.ok();
    }
}
