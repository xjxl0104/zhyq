package com.zhyq.park.energy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.entity.Reading;
import com.zhyq.park.energy.mapper.MeterMapper;
import com.zhyq.park.energy.mapper.ReadingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Tag(name = "能耗管理-抄表读数")
@RestController
@RequestMapping("/energy/reading")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingMapper readingMapper;
    private final MeterMapper meterMapper;

    @Operation(summary = "分页查询抄表读数")
    @GetMapping("/page")
    public Result<PageResult<Reading>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Long meterId,
                                            @RequestParam(required = false) String period) {
        LambdaQueryWrapper<Reading> qw = new LambdaQueryWrapper<>();
        qw.eq(meterId != null, Reading::getMeterId, meterId)
          .eq(StringUtils.hasText(period), Reading::getPeriod, period)
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
        // 抄表时间为空会被后续 ORDER BY read_time DESC 排到最末,
        // 该条抄表在统计/分摊/最新读数里会集体隐形,故兜底为当前时间
        if (reading.getReadTime() == null) {
            reading.setReadTime(LocalDateTime.now());
        }
        reading.setUsageAmount(calcUsage(reading));
        if (reading.getFee() == null) {
            reading.setFee(BigDecimal.ZERO);
        }
        readingMapper.insert(reading);
        syncMeterLastReading(reading.getMeterId());
        return Result.ok(reading.getId());
    }

    @Operation(summary = "修改抄表读数")
    @PutMapping
    public Result<Void> update(@RequestBody Reading reading) {
        // 请求体可能只带 {id, currReading},此时 meterId 为空会让倍率静默按 1 算、
        // 且回写上次读数时拿不到表计。先回查补齐。
        if (reading.getMeterId() == null && reading.getId() != null) {
            Reading exist = readingMapper.selectById(reading.getId());
            if (exist != null) {
                reading.setMeterId(exist.getMeterId());
                if (reading.getPrevReading() == null) {
                    reading.setPrevReading(exist.getPrevReading());
                }
                if (reading.getCurrReading() == null) {
                    reading.setCurrReading(exist.getCurrReading());
                }
            }
        }
        reading.setUsageAmount(calcUsage(reading));
        readingMapper.updateById(reading);
        syncMeterLastReading(reading.getMeterId());
        return Result.ok();
    }

    @Operation(summary = "删除抄表读数")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Reading exist = readingMapper.selectById(id);
        readingMapper.deleteById(id);
        if (exist != null) {
            syncMeterLastReading(exist.getMeterId());
        }
        return Result.ok();
    }

    /**
     * 用量 = (本次读数 - 上次读数) × 表计倍率,负数取 0。
     *
     * <p>倍率来自 {@code eng_meter.ratio}(互感器变比)。2026-09-11 之前此处不乘倍率,
     * 倍率列形同虚设 —— 园区电表倍率普遍为 30,不乘会让用量差 30 倍。经负责人拍板改为
     * 乘倍率。改动对存量无影响:改前库中全部表计 ratio 均为 1.00。
     *
     * <p>倍率缺失或 ≤0 时按 1 处理,避免历史脏数据把用量清零。
     */
    BigDecimal calcUsage(Reading reading) {
        BigDecimal prev = reading.getPrevReading() == null ? BigDecimal.ZERO : reading.getPrevReading();
        BigDecimal curr = reading.getCurrReading() == null ? BigDecimal.ZERO : reading.getCurrReading();
        BigDecimal diff = curr.subtract(prev);
        if (diff.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return diff.multiply(ratioOf(reading.getMeterId()))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal ratioOf(Long meterId) {
        if (meterId == null) {
            return BigDecimal.ONE;
        }
        Meter meter = meterMapper.selectById(meterId);
        if (meter == null || meter.getRatio() == null
                || meter.getRatio().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return meter.getRatio();
    }

    /**
     * 把表计的「上次读数」同步成该表最新一条抄表的本次读数。
     *
     * <p>原先新增抄表只写 eng_reading、从不回写 eng_meter.last_reading,表计列表里
     * 「上次读数」会永远停在初始值。这里按 read_time/id 取最新一条重算,
     * 因此补录、改数、删除都能得到正确结果。
     * 例外:删掉某块表的最后一条抄表时无从得知应回落到哪个值,保持原值不动。
     */
    private void syncMeterLastReading(Long meterId) {
        if (meterId == null) {
            return;
        }
        Reading latest = readingMapper.selectOne(new LambdaQueryWrapper<Reading>()
                .eq(Reading::getMeterId, meterId)
                .orderByDesc(Reading::getReadTime)
                .orderByDesc(Reading::getId)
                .last("LIMIT 1"));
        if (latest == null || latest.getCurrReading() == null) {
            return;
        }
        meterMapper.update(null, new LambdaUpdateWrapper<Meter>()
                .eq(Meter::getId, meterId)
                .set(Meter::getLastReading, latest.getCurrReading()));
    }
}
