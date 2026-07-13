package com.zhyq.park.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.tenant.entity.TenantStaff;
import com.zhyq.park.tenant.mapper.TenantStaffMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "租客-租客员工")
@RestController
@RequestMapping("/tenant/staff")
@RequiredArgsConstructor
public class TenantStaffController {

    private final TenantStaffMapper staffMapper;

    @Operation(summary = "分页查询租客员工")
    @GetMapping("/page")
    public Result<PageResult<TenantStaff>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) Long tenantRefId,
                                                @RequestParam(required = false) String name,
                                                @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<TenantStaff> qw = new LambdaQueryWrapper<>();
        qw.eq(tenantRefId != null, TenantStaff::getTenantRefId, tenantRefId)
          .like(StringUtils.hasText(name), TenantStaff::getName, name)
          .eq(status != null, TenantStaff::getStatus, status)
          .orderByDesc(TenantStaff::getId);
        IPage<TenantStaff> p = staffMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "租客员工详情")
    @GetMapping("/{id}")
    public Result<TenantStaff> get(@PathVariable Long id) {
        return Result.ok(staffMapper.selectById(id));
    }

    @Operation(summary = "新增租客员工")
    @PostMapping
    public Result<Long> add(@RequestBody TenantStaff staff) {
        staffMapper.insert(staff);
        return Result.ok(staff.getId());
    }

    @Operation(summary = "修改租客员工")
    @PutMapping
    public Result<Void> update(@RequestBody TenantStaff staff) {
        staffMapper.updateById(staff);
        return Result.ok();
    }

    @Operation(summary = "删除租客员工")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        staffMapper.deleteById(id);
        return Result.ok();
    }
}
