package com.zhyq.park.iot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.iot.entity.Vendor;
import com.zhyq.park.iot.mapper.VendorMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "智慧物联-厂商配置")
@RestController
@RequestMapping("/iot/vendor")
@RequiredArgsConstructor
public class VendorController {

    private final VendorMapper vendorMapper;

    @Operation(summary = "分页查询厂商")
    @GetMapping("/page")
    public Result<PageResult<Vendor>> page(@RequestParam(defaultValue = "1") int pageNo,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String name,
                                           @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Vendor> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), Vendor::getName, name)
          .eq(status != null, Vendor::getStatus, status)
          .orderByDesc(Vendor::getId);
        IPage<Vendor> p = vendorMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "厂商详情")
    @GetMapping("/{id}")
    public Result<Vendor> get(@PathVariable Long id) {
        return Result.ok(vendorMapper.selectById(id));
    }

    @Operation(summary = "新增厂商")
    @PostMapping
    public Result<Long> add(@RequestBody Vendor vendor) {
        vendorMapper.insert(vendor);
        return Result.ok(vendor.getId());
    }

    @Operation(summary = "修改厂商")
    @PutMapping
    public Result<Void> update(@RequestBody Vendor vendor) {
        vendorMapper.updateById(vendor);
        return Result.ok();
    }

    @Operation(summary = "删除厂商")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        vendorMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "连通测试(模拟)")
    @PostMapping("/{id}/test")
    public Result<String> test(@PathVariable Long id) {
        Vendor vendor = vendorMapper.selectById(id);
        if (vendor == null) {
            throw new BizException("厂商配置不存在");
        }
        if (vendor.getStatus() != null && vendor.getStatus() == 1) {
            return Result.ok("连接成功(模拟)");
        }
        throw new BizException("厂商平台已停用");
    }
}
