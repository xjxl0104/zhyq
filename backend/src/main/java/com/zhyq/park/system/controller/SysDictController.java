package com.zhyq.park.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.system.entity.SysDictData;
import com.zhyq.park.system.entity.SysDictType;
import com.zhyq.park.system.mapper.SysDictDataMapper;
import com.zhyq.park.system.mapper.SysDictTypeMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统管理-字典")
@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;

    @Operation(summary = "字典类型分页")
    @GetMapping("/type/page")
    public Result<PageResult<SysDictType>> typePage(@RequestParam(defaultValue = "1") int pageNo,
                                                    @RequestParam(defaultValue = "10") int pageSize,
                                                    @RequestParam(required = false) String dictName) {
        LambdaQueryWrapper<SysDictType> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(dictName), SysDictType::getDictName, dictName).orderByDesc(SysDictType::getId);
        IPage<SysDictType> p = typeMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @PostMapping("/type")
    public Result<Long> addType(@RequestBody SysDictType t) {
        typeMapper.insert(t);
        return Result.ok(t.getId());
    }

    @PutMapping("/type")
    public Result<Void> updateType(@RequestBody SysDictType t) {
        typeMapper.updateById(t);
        return Result.ok();
    }

    @DeleteMapping("/type/{id}")
    public Result<Void> delType(@PathVariable Long id) {
        typeMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "按类型取字典项")
    @GetMapping("/data/{dictType}")
    public Result<List<SysDictData>> dataByType(@PathVariable String dictType) {
        return Result.ok(dataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, 1)
                .orderByAsc(SysDictData::getSort)));
    }

    @PostMapping("/data")
    public Result<Long> addData(@RequestBody SysDictData d) {
        dataMapper.insert(d);
        return Result.ok(d.getId());
    }

    @PutMapping("/data")
    public Result<Void> updateData(@RequestBody SysDictData d) {
        dataMapper.updateById(d);
        return Result.ok();
    }

    @DeleteMapping("/data/{id}")
    public Result<Void> delData(@PathVariable Long id) {
        dataMapper.deleteById(id);
        return Result.ok();
    }
}
