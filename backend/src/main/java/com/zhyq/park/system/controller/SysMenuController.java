package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysMenu;
import com.zhyq.park.system.mapper.SysMenuMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统管理-菜单")
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuMapper menuMapper;

    @Operation(summary = "菜单列表(树形由前端组装)")
    @PreAuthorize("hasAuthority('system:menu:query')")
    @GetMapping("/list")
    public Result<List<SysMenu>> list() {
        return Result.ok(menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort)));
    }

    @Operation(summary = "菜单详情")
    @PreAuthorize("hasAuthority('system:menu:query')")
    @GetMapping("/{id}")
    public Result<SysMenu> get(@PathVariable Long id) {
        return Result.ok(menuMapper.selectById(id));
    }

    @Operation(summary = "新增菜单")
    @PreAuthorize("hasAuthority('system:menu:add')")
    @PostMapping
    public Result<Long> add(@RequestBody SysMenu menu) {
        menuMapper.insert(menu);
        return Result.ok(menu.getId());
    }

    @Operation(summary = "修改菜单")
    @PreAuthorize("hasAuthority('system:menu:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody SysMenu menu) {
        menuMapper.updateById(menu);
        return Result.ok();
    }

    @Operation(summary = "删除菜单")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuMapper.deleteById(id);
        return Result.ok();
    }
}
