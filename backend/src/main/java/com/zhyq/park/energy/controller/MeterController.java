package com.zhyq.park.energy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.mapper.MeterMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "能耗管理-表计")
@RestController
@RequestMapping("/energy/meter")
@RequiredArgsConstructor
public class MeterController {

    private final MeterMapper meterMapper;

    @Operation(summary = "分页查询表计")
    @GetMapping("/page")
    public Result<PageResult<Meter>> page(@RequestParam(defaultValue = "1") int pageNo,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String code,
                                          @RequestParam(required = false) String energyType,
                                          @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Meter> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(code), Meter::getCode, code)
          .eq(StringUtils.hasText(energyType), Meter::getEnergyType, energyType)
          .eq(status != null, Meter::getStatus, status)
          .orderByDesc(Meter::getId);
        IPage<Meter> p = meterMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "表计详情")
    @GetMapping("/{id}")
    public Result<Meter> get(@PathVariable Long id) {
        return Result.ok(meterMapper.selectById(id));
    }

    @Operation(summary = "新增表计")
    @PostMapping
    public Result<Long> add(@RequestBody Meter meter) {
        meterMapper.insert(meter);
        return Result.ok(meter.getId());
    }

    @Operation(summary = "修改表计")
    @PutMapping
    public Result<Void> update(@RequestBody Meter meter) {
        meterMapper.updateById(meter);
        return Result.ok();
    }

    @Operation(summary = "删除表计")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        meterMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "全部在用表计(下拉)")
    @GetMapping("/list")
    public Result<List<Meter>> list() {
        return Result.ok(meterMapper.selectList(
                new LambdaQueryWrapper<Meter>().eq(Meter::getStatus, 1).orderByDesc(Meter::getId)));
    }

    @Operation(summary = "按能源类型统计表计数量")
    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> stats() {
        List<Meter> all = meterMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (Meter m : all) {
            String type = StringUtils.hasText(m.getEnergyType()) ? m.getEnergyType() : "未分类";
            grouped.merge(type, 1, Integer::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("energyType", k);
            item.put("count", v);
            result.add(item);
        });
        return Result.ok(result);
    }
}
