package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.dto.RolePermissionRequest;
import com.zhyq.park.system.entity.SysRole;
import com.zhyq.park.system.mapper.SysRoleMapper;
import com.zhyq.park.system.service.RoleManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统管理-角色")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleMapper roleMapper;
    private final RoleManagementService roleManagementService;

    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/page")
    public Result<PageResult<SysRole>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), SysRole::getName, name).orderByAsc(SysRole::getSort);
        IPage<SysRole> p = roleMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.ok(roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1)));
    }

    @PreAuthorize("hasAuthority('system:role:add')")
    @PostMapping
    public Result<Long> add(@RequestBody SysRole role) {
        return Result.ok(roleManagementService.add(role));
    }

    @PreAuthorize("hasAuthority('system:role:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody SysRole role) {
        roleManagementService.update(role);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:role:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleManagementService.delete(id);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/{id}/menu-ids")
    public Result<List<Long>> menuIds(@PathVariable Long id) {
        return Result.ok(roleManagementService.getMenuIds(id));
    }

    @PreAuthorize("hasAuthority('system:role:permission')")
    @PutMapping("/{id}/menu-ids")
    public Result<Void> saveMenuIds(@PathVariable Long id,
                                    @RequestBody RolePermissionRequest request) {
        roleManagementService.saveMenuIds(id, request.getMenuIds());
        return Result.ok();
    }
}
