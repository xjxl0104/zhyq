package com.zhyq.park.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.tenant.entity.TenantMessage;
import com.zhyq.park.tenant.mapper.TenantMessageMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "租客-站内信")
@RestController
@RequestMapping("/tenant/message")
@RequiredArgsConstructor
public class TenantMessageController {

    private final TenantMessageMapper messageMapper;

    @Operation(summary = "分页查询站内信")
    @GetMapping("/page")
    public Result<PageResult<TenantMessage>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) String title,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) Long tenantRefId) {
        LambdaQueryWrapper<TenantMessage> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), TenantMessage::getTitle, title)
          .eq(status != null, TenantMessage::getStatus, status)
          .eq(tenantRefId != null, TenantMessage::getTenantRefId, tenantRefId)
          .orderByDesc(TenantMessage::getId);
        IPage<TenantMessage> p = messageMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "站内信详情")
    @GetMapping("/{id}")
    public Result<TenantMessage> get(@PathVariable Long id) {
        return Result.ok(messageMapper.selectById(id));
    }

    @Operation(summary = "新增站内信(草稿)")
    @PostMapping
    public Result<Long> add(@RequestBody TenantMessage message) {
        messageMapper.insert(message);
        return Result.ok(message.getId());
    }

    @Operation(summary = "修改站内信")
    @PutMapping
    public Result<Void> update(@RequestBody TenantMessage message) {
        messageMapper.updateById(message);
        return Result.ok();
    }

    @Operation(summary = "删除站内信")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        messageMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "发送站内信(仅草稿可发送)")
    @PostMapping("/{id}/send")
    public Result<Void> send(@PathVariable Long id) {
        int updated = messageMapper.update(null, new LambdaUpdateWrapper<TenantMessage>()
                .eq(TenantMessage::getId, id)
                .eq(TenantMessage::getStatus, 1)
                .set(TenantMessage::getStatus, 2)
                .set(TenantMessage::getSendTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException("仅草稿可发送");
        }
        return Result.ok();
    }
}
