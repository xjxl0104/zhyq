package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.Activity;
import com.zhyq.park.property.mapper.ActivityMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 物业活动
 * 状态:1报名中 2进行中 3已结束
 */
@Tag(name = "物业-物业活动")
@RestController
@RequestMapping("/property/activity")
@RequiredArgsConstructor
public class ActivityController {

    /** 状态:报名中 */
    public static final int ST_ENROLLING = 1;
    /** 状态:进行中 */
    public static final int ST_ONGOING = 2;
    /** 状态:已结束 */
    public static final int ST_FINISHED = 3;

    private final ActivityMapper activityMapper;

    @Operation(summary = "分页查询活动")
    @GetMapping("/page")
    public Result<PageResult<Activity>> page(@RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(required = false) String title) {
        LambdaQueryWrapper<Activity> qw = new LambdaQueryWrapper<>();
        qw.eq(status != null, Activity::getStatus, status)
          .like(StringUtils.hasText(title), Activity::getTitle, title)
          .orderByDesc(Activity::getId);
        IPage<Activity> p = activityMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "活动详情")
    @GetMapping("/{id}")
    public Result<Activity> get(@PathVariable Long id) {
        return Result.ok(activityMapper.selectById(id));
    }

    @Operation(summary = "新增活动")
    @PostMapping
    public Result<Long> add(@RequestBody Activity activity) {
        if (activity.getStatus() == null) {
            activity.setStatus(ST_ENROLLING);
        }
        if (activity.getEnrollCount() == null) {
            activity.setEnrollCount(0);
        }
        activityMapper.insert(activity);
        return Result.ok(activity.getId());
    }

    @Operation(summary = "修改活动")
    @PutMapping
    public Result<Void> update(@RequestBody Activity activity) {
        activityMapper.updateById(activity);
        return Result.ok();
    }

    @Operation(summary = "删除活动")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        activityMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "报名")
    @PostMapping("/{id}/enroll")
    public Result<Void> enroll(@PathVariable Long id) {
        LambdaUpdateWrapper<Activity> uw = new LambdaUpdateWrapper<>();
        uw.eq(Activity::getId, id)
          .eq(Activity::getStatus, ST_ENROLLING)
          .setSql("enroll_count = enroll_count + 1");
        int updated = activityMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("仅报名中的活动可报名");
        }
        return Result.ok();
    }
}
