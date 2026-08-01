package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.dto.SysUserDetailResponse;
import com.zhyq.park.system.dto.SysUserSaveRequest;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
import com.zhyq.park.system.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统管理-用户")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserMapper userMapper;
    private final UserManagementService userManagementService;

    @Operation(summary = "分页查询用户")
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String username,
                                            @RequestParam(required = false) String nickname,
                                            @RequestParam(required = false) Long deptId,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(username), SysUser::getUsername, username)
          .like(StringUtils.hasText(nickname), SysUser::getNickname, nickname)
          .eq(deptId != null, SysUser::getDeptId, deptId)
          .eq(status != null, SysUser::getStatus, status)
          .orderByDesc(SysUser::getId);
        IPage<SysUser> p = userMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        userManagementService.fillRoleNames(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "用户详情")
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/{id}")
    public Result<SysUserDetailResponse> get(@PathVariable Long id) {
        return Result.ok(userManagementService.get(id));
    }

    @Operation(summary = "新增用户并分配角色")
    @PreAuthorize("hasAuthority('system:user:add') and hasAuthority('system:user:role')")
    @PostMapping
    public Result<Long> add(@RequestBody SysUserSaveRequest request) {
        return Result.ok(userManagementService.create(request));
    }

    @Operation(summary = "修改用户并重新分配角色")
    @PreAuthorize("hasAuthority('system:user:edit') and hasAuthority('system:user:role')")
    @PutMapping
    public Result<Void> update(@RequestBody SysUserSaveRequest request) {
        userManagementService.update(request);
        return Result.ok();
    }

    @Operation(summary = "删除用户")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userManagementService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "全部用户(下拉)")
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/list")
    public Result<List<SysUser>> list() {
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1));
        userManagementService.fillRoleNames(users);
        return Result.ok(users);
    }
}
