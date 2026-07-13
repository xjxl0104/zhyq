package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysRole;
import com.zhyq.park.system.mapper.SysRoleMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统管理-角色")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleMapper roleMapper;

    @GetMapping("/page")
    public Result<PageResult<SysRole>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), SysRole::getName, name).orderByAsc(SysRole::getSort);
        IPage<SysRole> p = roleMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.ok(roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1)));
    }

    @PostMapping
    public Result<Long> add(@RequestBody SysRole role) {
        roleMapper.insert(role);
        return Result.ok(role.getId());
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysRole role) {
        roleMapper.updateById(role);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleMapper.deleteById(id);
        return Result.ok();
    }
}
