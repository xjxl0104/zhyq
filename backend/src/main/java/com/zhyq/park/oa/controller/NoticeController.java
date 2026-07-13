package com.zhyq.park.oa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.oa.entity.Notice;
import com.zhyq.park.oa.mapper.NoticeMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "办公管理-公告")
@RestController
@RequestMapping("/oa/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeMapper noticeMapper;

    @Operation(summary = "分页查询公告")
    @GetMapping("/page")
    public Result<PageResult<Notice>> page(@RequestParam(defaultValue = "1") int pageNo,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String title,
                                           @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Notice> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), Notice::getTitle, title)
          .eq(status != null, Notice::getStatus, status)
          .orderByDesc(Notice::getId);
        IPage<Notice> p = noticeMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "公告详情")
    @GetMapping("/{id}")
    public Result<Notice> get(@PathVariable Long id) {
        return Result.ok(noticeMapper.selectById(id));
    }

    @Operation(summary = "新增公告")
    @PostMapping
    public Result<Long> add(@RequestBody Notice notice) {
        noticeMapper.insert(notice);
        return Result.ok(notice.getId());
    }

    @Operation(summary = "修改公告")
    @PutMapping
    public Result<Void> update(@RequestBody Notice notice) {
        noticeMapper.updateById(notice);
        return Result.ok();
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        return Result.ok();
    }
}
