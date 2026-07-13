package com.zhyq.park.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Channel;
import com.zhyq.park.crm.mapper.ChannelMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "招商-渠道商")
@RestController
@RequestMapping("/crm/channel")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelMapper channelMapper;

    @Operation(summary = "分页查询渠道")
    @GetMapping("/page")
    public Result<PageResult<Channel>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String contact,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Channel> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), Channel::getName, name)
          .like(StringUtils.hasText(contact), Channel::getContact, contact)
          .eq(status != null, Channel::getStatus, status)
          .orderByDesc(Channel::getId);
        IPage<Channel> p = channelMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "渠道详情")
    @GetMapping("/{id}")
    public Result<Channel> get(@PathVariable Long id) {
        return Result.ok(channelMapper.selectById(id));
    }

    @Operation(summary = "新增渠道")
    @PostMapping
    public Result<Long> add(@RequestBody Channel channel) {
        channelMapper.insert(channel);
        return Result.ok(channel.getId());
    }

    @Operation(summary = "修改渠道")
    @PutMapping
    public Result<Void> update(@RequestBody Channel channel) {
        channelMapper.updateById(channel);
        return Result.ok();
    }

    @Operation(summary = "删除渠道")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "全部渠道(下拉)")
    @GetMapping("/list")
    public Result<List<Channel>> list() {
        return Result.ok(channelMapper.selectList(new LambdaQueryWrapper<Channel>().eq(Channel::getStatus, 1)));
    }
}
