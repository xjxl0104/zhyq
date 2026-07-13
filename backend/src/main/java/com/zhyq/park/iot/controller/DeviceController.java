package com.zhyq.park.iot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.iot.entity.Device;
import com.zhyq.park.iot.mapper.DeviceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "智慧物联-设备")
@RestController
@RequestMapping("/iot/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceMapper deviceMapper;

    @Operation(summary = "分页查询设备")
    @GetMapping("/page")
    public Result<PageResult<Device>> page(@RequestParam(defaultValue = "1") int pageNo,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String code,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Device> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(code), Device::getCode, code)
          .eq(StringUtils.hasText(category), Device::getCategory, category)
          .eq(status != null, Device::getStatus, status)
          .orderByDesc(Device::getId);
        IPage<Device> p = deviceMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "设备详情")
    @GetMapping("/{id}")
    public Result<Device> get(@PathVariable Long id) {
        return Result.ok(deviceMapper.selectById(id));
    }

    @Operation(summary = "新增设备")
    @PostMapping
    public Result<Long> add(@RequestBody Device device) {
        deviceMapper.insert(device);
        return Result.ok(device.getId());
    }

    @Operation(summary = "修改设备")
    @PutMapping
    public Result<Void> update(@RequestBody Device device) {
        deviceMapper.updateById(device);
        return Result.ok();
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "全部设备(下拉)")
    @GetMapping("/list")
    public Result<List<Device>> list() {
        return Result.ok(deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().orderByDesc(Device::getId)));
    }

    @Operation(summary = "设备统计:在线/离线/分类")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        List<Device> all = deviceMapper.selectList(new LambdaQueryWrapper<>());
        long online = all.stream().filter(d -> d.getStatus() != null && d.getStatus() == 1).count();
        long offline = all.stream().filter(d -> d.getStatus() != null && d.getStatus() == 2).count();

        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (Device d : all) {
            String cat = StringUtils.hasText(d.getCategory()) ? d.getCategory() : "未分类";
            grouped.merge(cat, 1, Integer::sum);
        }
        List<Map<String, Object>> categoryList = new ArrayList<>();
        grouped.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", k);
            item.put("count", v);
            categoryList.add(item);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("online", online);
        result.put("offline", offline);
        result.put("categoryList", categoryList);
        return Result.ok(result);
    }
}
