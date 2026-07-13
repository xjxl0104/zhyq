package com.zhyq.park.iot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.iot.entity.IotChannel;
import com.zhyq.park.iot.mapper.IotChannelMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "智慧物联-通道")
@RestController
@RequestMapping("/iot/channel")
@RequiredArgsConstructor
public class IotChannelController {

    private final IotChannelMapper channelMapper;

    @Operation(summary = "分页查询通道")
    @GetMapping("/page")
    public Result<PageResult<IotChannel>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Long deviceId,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<IotChannel> qw = new LambdaQueryWrapper<>();
        qw.eq(deviceId != null, IotChannel::getDeviceId, deviceId)
          .eq(status != null, IotChannel::getStatus, status)
          .orderByDesc(IotChannel::getId);
        IPage<IotChannel> p = channelMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "通道详情")
    @GetMapping("/{id}")
    public Result<IotChannel> get(@PathVariable Long id) {
        return Result.ok(channelMapper.selectById(id));
    }

    @Operation(summary = "新增通道")
    @PostMapping
    public Result<Long> add(@RequestBody IotChannel channel) {
        channelMapper.insert(channel);
        return Result.ok(channel.getId());
    }

    @Operation(summary = "修改通道")
    @PutMapping
    public Result<Void> update(@RequestBody IotChannel channel) {
        channelMapper.updateById(channel);
        return Result.ok();
    }

    @Operation(summary = "删除通道")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "按设备查通道(下拉)")
    @GetMapping("/list")
    public Result<List<IotChannel>> list(@RequestParam(required = false) Long deviceId) {
        LambdaQueryWrapper<IotChannel> qw = new LambdaQueryWrapper<>();
        qw.eq(deviceId != null, IotChannel::getDeviceId, deviceId)
          .orderByDesc(IotChannel::getId);
        return Result.ok(channelMapper.selectList(qw));
    }
}
