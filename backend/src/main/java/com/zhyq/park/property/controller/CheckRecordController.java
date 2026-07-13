package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.CheckRecord;
import com.zhyq.park.property.mapper.CheckRecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "物业-三检(保洁/绿化/品质)")
@RestController
@RequestMapping("/property/check")
@RequiredArgsConstructor
public class CheckRecordController {

    private final CheckRecordMapper checkRecordMapper;

    @Operation(summary = "分页查询检查记录")
    @GetMapping("/page")
    public Result<PageResult<CheckRecord>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String ctype,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) String location) {
        LambdaQueryWrapper<CheckRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(ctype), CheckRecord::getCtype, ctype)
          .eq(status != null, CheckRecord::getStatus, status)
          .like(StringUtils.hasText(location), CheckRecord::getLocation, location)
          .orderByDesc(CheckRecord::getId);
        IPage<CheckRecord> p = checkRecordMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "检查记录详情")
    @GetMapping("/{id}")
    public Result<CheckRecord> get(@PathVariable Long id) {
        return Result.ok(checkRecordMapper.selectById(id));
    }

    @Operation(summary = "新增检查记录")
    @PostMapping
    public Result<Long> add(@RequestBody CheckRecord record) {
        checkRecordMapper.insert(record);
        return Result.ok(record.getId());
    }

    @Operation(summary = "修改检查记录")
    @PutMapping
    public Result<Void> update(@RequestBody CheckRecord record) {
        checkRecordMapper.updateById(record);
        return Result.ok();
    }

    @Operation(summary = "删除检查记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        checkRecordMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "整改完成")
    @PostMapping("/{id}/rectify")
    public Result<Void> rectify(@PathVariable Long id) {
        int updated = checkRecordMapper.update(null,
                new LambdaUpdateWrapper<CheckRecord>()
                        .eq(CheckRecord::getId, id)
                        .eq(CheckRecord::getStatus, 2)
                        .set(CheckRecord::getStatus, 3));
        if (updated == 0) {
            throw new BizException("仅待整改记录可操作");
        }
        return Result.ok();
    }
}
