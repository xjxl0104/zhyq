package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.Feedback;
import com.zhyq.park.property.mapper.FeedbackMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 投诉与意见反馈
 * 状态:1待处理 2处理中 3已办结
 */
@Tag(name = "物业-投诉反馈")
@RestController
@RequestMapping("/property/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    /** 状态:待处理 */
    public static final int ST_PENDING = 1;
    /** 状态:处理中 */
    public static final int ST_PROCESSING = 2;
    /** 状态:已办结 */
    public static final int ST_DONE = 3;

    private final FeedbackMapper feedbackMapper;

    @Operation(summary = "分页查询反馈")
    @GetMapping("/page")
    public Result<PageResult<Feedback>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String ftype,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(required = false) String title) {
        LambdaQueryWrapper<Feedback> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(ftype), Feedback::getFtype, ftype)
          .eq(status != null, Feedback::getStatus, status)
          .like(StringUtils.hasText(title), Feedback::getTitle, title)
          .orderByDesc(Feedback::getId);
        IPage<Feedback> p = feedbackMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "反馈详情")
    @GetMapping("/{id}")
    public Result<Feedback> get(@PathVariable Long id) {
        return Result.ok(feedbackMapper.selectById(id));
    }

    @Operation(summary = "新增反馈")
    @PostMapping
    public Result<Long> add(@RequestBody Feedback feedback) {
        if (feedback.getStatus() == null) {
            feedback.setStatus(ST_PENDING);
        }
        feedbackMapper.insert(feedback);
        return Result.ok(feedback.getId());
    }

    @Operation(summary = "修改反馈")
    @PutMapping
    public Result<Void> update(@RequestBody Feedback feedback) {
        feedbackMapper.updateById(feedback);
        return Result.ok();
    }

    @Operation(summary = "删除反馈")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        feedbackMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "开始处理")
    @PostMapping("/{id}/processing")
    public Result<Void> processing(@PathVariable Long id) {
        LambdaUpdateWrapper<Feedback> uw = new LambdaUpdateWrapper<>();
        uw.eq(Feedback::getId, id)
          .eq(Feedback::getStatus, ST_PENDING)
          .set(Feedback::getStatus, ST_PROCESSING);
        int updated = feedbackMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("该记录已办结");
        }
        return Result.ok();
    }

    @Operation(summary = "处理办结")
    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reply = strOf(body.get("reply"));
        String handler = strOf(body.get("handler"));
        LambdaUpdateWrapper<Feedback> uw = new LambdaUpdateWrapper<>();
        uw.eq(Feedback::getId, id)
          .in(Feedback::getStatus, ST_PENDING, ST_PROCESSING)
          .set(Feedback::getStatus, ST_DONE)
          .set(Feedback::getReply, reply)
          .set(Feedback::getHandler, handler);
        int updated = feedbackMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("该记录已办结");
        }
        return Result.ok();
    }

    private static String strOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
