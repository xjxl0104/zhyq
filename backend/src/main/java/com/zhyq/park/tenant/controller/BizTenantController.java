package com.zhyq.park.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.tenant.entity.BizTenant;
import com.zhyq.park.tenant.mapper.BizTenantMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "租客-租客信息")
@RestController
@RequestMapping("/tenant/info")
@RequiredArgsConstructor
public class BizTenantController {

    private final BizTenantMapper tenantMapper;

    @Operation(summary = "分页查询租客")
    @GetMapping("/page")
    public Result<PageResult<BizTenant>> page(@RequestParam(defaultValue = "1") int pageNo,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) String name,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(required = false) Integer tenantType,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String industry) {
        LambdaQueryWrapper<BizTenant> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), BizTenant::getName, name)
          .like(StringUtils.hasText(code), BizTenant::getCode, code)
          .eq(tenantType != null, BizTenant::getTenantType, tenantType)
          .eq(status != null, BizTenant::getStatus, status)
          .like(StringUtils.hasText(industry), BizTenant::getIndustry, industry)
          .orderByDesc(BizTenant::getId);
        IPage<BizTenant> p = tenantMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "租客统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> map = new HashMap<>();
        long total = tenantMapper.selectCount(new LambdaQueryWrapper<>());
        long enterprise = tenantMapper.selectCount(
                new LambdaQueryWrapper<BizTenant>().eq(BizTenant::getTenantType, 1));
        long personal = tenantMapper.selectCount(
                new LambdaQueryWrapper<BizTenant>().eq(BizTenant::getTenantType, 2));
        map.put("total", total);
        map.put("enterprise", enterprise);
        map.put("personal", personal);
        map.put("contractCount", 0);
        return Result.ok(map);
    }

    @Operation(summary = "租客详情")
    @GetMapping("/{id}")
    public Result<BizTenant> get(@PathVariable Long id) {
        return Result.ok(tenantMapper.selectById(id));
    }

    @Operation(summary = "新增租客")
    @PostMapping
    public Result<Long> add(@RequestBody BizTenant tenant) {
        tenantMapper.insert(tenant);
        return Result.ok(tenant.getId());
    }

    @Operation(summary = "修改租客")
    @PutMapping
    public Result<Void> update(@RequestBody BizTenant tenant) {
        tenantMapper.updateById(tenant);
        return Result.ok();
    }

    @Operation(summary = "删除租客")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tenantMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "归档租客")
    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        BizTenant tenant = new BizTenant();
        tenant.setId(id);
        tenant.setStatus(2);
        tenantMapper.updateById(tenant);
        return Result.ok();
    }

    @Operation(summary = "全部租客(下拉,仅正常)")
    @GetMapping("/list")
    public Result<List<BizTenant>> list() {
        return Result.ok(tenantMapper.selectList(
                new LambdaQueryWrapper<BizTenant>().eq(BizTenant::getStatus, 1).orderByDesc(BizTenant::getId)));
    }
}
