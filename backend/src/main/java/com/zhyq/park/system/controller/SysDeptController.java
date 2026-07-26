package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysDept;
import com.zhyq.park.system.mapper.SysDeptMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统管理-部门")
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptMapper deptMapper;

    @Operation(summary = "部门列表(树形由前端组装)")
    @PreAuthorize("hasAuthority('system:dept:query')")
    @GetMapping("/list")
    public Result<List<SysDept>> list() {
        return Result.ok(deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSort)));
    }

    @PreAuthorize("hasAuthority('system:dept:add')")
    @PostMapping
    public Result<Long> add(@RequestBody SysDept dept) {
        deptMapper.insert(dept);
        return Result.ok(dept.getId());
    }

    @PreAuthorize("hasAuthority('system:dept:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody SysDept dept) {
        deptMapper.updateById(dept);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:dept:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deptMapper.deleteById(id);
        return Result.ok();
    }
}
