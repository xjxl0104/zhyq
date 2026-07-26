package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysUser;
import com.zhyq.park.system.mapper.SysUserMapper;
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
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "用户详情")
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/{id}")
    public Result<SysUser> get(@PathVariable Long id) {
        return Result.ok(userMapper.selectById(id));
    }

    @Operation(summary = "新增用户")
    @PreAuthorize("hasAuthority('system:user:add')")
    @PostMapping
    public Result<Long> add(@RequestBody SysUser user) {
        userMapper.insert(user);
        return Result.ok(user.getId());
    }

    @Operation(summary = "修改用户")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @PutMapping
    public Result<Void> update(@RequestBody SysUser user) {
        userMapper.updateById(user);
        return Result.ok();
    }

    @Operation(summary = "删除用户")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "全部用户(下拉)")
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/list")
    public Result<List<SysUser>> list() {
        return Result.ok(userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1)));
    }
}
