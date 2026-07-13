package com.zhyq.park.energy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.energy.entity.Reading;
import com.zhyq.park.energy.mapper.ReadingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "能耗管理-抄表读数")
@RestController
@RequestMapping("/energy/reading")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingMapper readingMapper;

    @Operation(summary = "分页查询抄表读数")
    @GetMapping("/page")
    public Result<PageResult<Reading>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Long meterId) {
        LambdaQueryWrapper<Reading> qw = new LambdaQueryWrapper<>();
        qw.eq(meterId != null, Reading::getMeterId, meterId)
          .orderByDesc(Reading::getId);
        IPage<Reading> p = readingMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "抄表读数详情")
    @GetMapping("/{id}")
    public Result<Reading> get(@PathVariable Long id) {
        return Result.ok(readingMapper.selectById(id));
    }

    @Operation(summary = "新增抄表读数")
    @PostMapping
    public Result<Long> add(@RequestBody Reading reading) {
        // 用量 = 本次读数 - 上次读数,负数则取 0;费用暂不计算,留 0
        BigDecimal prev = reading.getPrevReading() == null ? BigDecimal.ZERO : reading.getPrevReading();
        BigDecimal curr = reading.getCurrReading() == null ? BigDecimal.ZERO : reading.getCurrReading();
        BigDecimal usage = curr.subtract(prev);
        if (usage.compareTo(BigDecimal.ZERO) < 0) {
            usage = BigDecimal.ZERO;
        }
        reading.setUsageAmount(usage);
        if (reading.getFee() == null) {
            reading.setFee(BigDecimal.ZERO);
        }
        readingMapper.insert(reading);
        return Result.ok(reading.getId());
    }

    @Operation(summary = "修改抄表读数")
    @PutMapping
    public Result<Void> update(@RequestBody Reading reading) {
        BigDecimal prev = reading.getPrevReading() == null ? BigDecimal.ZERO : reading.getPrevReading();
        BigDecimal curr = reading.getCurrReading() == null ? BigDecimal.ZERO : reading.getCurrReading();
        BigDecimal usage = curr.subtract(prev);
        if (usage.compareTo(BigDecimal.ZERO) < 0) {
            usage = BigDecimal.ZERO;
        }
        reading.setUsageAmount(usage);
        readingMapper.updateById(reading);
        return Result.ok();
    }

    @Operation(summary = "删除抄表读数")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        readingMapper.deleteById(id);
        return Result.ok();
    }
}
