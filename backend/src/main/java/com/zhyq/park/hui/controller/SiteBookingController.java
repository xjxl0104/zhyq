package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.SiteBooking;
import com.zhyq.park.hui.mapper.SiteBookingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 惠企服务-场地预约
 * 状态:1待审批 2已通过 3已取消
 */
@Tag(name = "惠企服务-场地预约")
@RestController
@RequestMapping("/service/site")
@RequiredArgsConstructor
public class SiteBookingController {

    /** 状态:待审批 */
    public static final int ST_PENDING = 1;
    /** 状态:已通过 */
    public static final int ST_APPROVED = 2;
    /** 状态:已取消 */
    public static final int ST_CANCELLED = 3;

    private final SiteBookingMapper siteBookingMapper;

    @Operation(summary = "分页查询场地预约")
    @GetMapping("/page")
    public Result<PageResult<SiteBooking>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String siteName,
                                                 @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SiteBooking> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(siteName), SiteBooking::getSiteName, siteName)
          .eq(status != null, SiteBooking::getStatus, status)
          .orderByDesc(SiteBooking::getId);
        IPage<SiteBooking> p = siteBookingMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "场地预约详情")
    @GetMapping("/{id}")
    public Result<SiteBooking> get(@PathVariable Long id) {
        return Result.ok(siteBookingMapper.selectById(id));
    }

    @Operation(summary = "新增场地预约(校验时间冲突)")
    @PostMapping
    public Result<Long> add(@RequestBody SiteBooking booking) {
        if (booking.getStatus() == null) {
            booking.setStatus(ST_PENDING);
        }
        checkConflict(booking, null);
        siteBookingMapper.insert(booking);
        return Result.ok(booking.getId());
    }

    @Operation(summary = "修改场地预约(校验时间冲突)")
    @PutMapping
    public Result<Void> update(@RequestBody SiteBooking booking) {
        checkConflict(booking, booking.getId());
        siteBookingMapper.updateById(booking);
        return Result.ok();
    }

    @Operation(summary = "删除场地预约")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        siteBookingMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "审批通过(1→2)")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        LambdaUpdateWrapper<SiteBooking> uw = new LambdaUpdateWrapper<>();
        uw.eq(SiteBooking::getId, id)
          .eq(SiteBooking::getStatus, ST_PENDING)
          .set(SiteBooking::getStatus, ST_APPROVED);
        int updated = siteBookingMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("仅待审批状态可通过");
        }
        return Result.ok();
    }

    @Operation(summary = "取消预约(→3)")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        LambdaUpdateWrapper<SiteBooking> uw = new LambdaUpdateWrapper<>();
        uw.eq(SiteBooking::getId, id)
          .ne(SiteBooking::getStatus, ST_CANCELLED)
          .set(SiteBooking::getStatus, ST_CANCELLED);
        int updated = siteBookingMapper.update(null, uw);
        if (updated == 0) {
            throw new BizException("该预约已取消");
        }
        return Result.ok();
    }

    /**
     * 时间冲突校验:同一场地(siteName),存在非取消状态的预约,且时间段重叠(新start < 旧end 且 新end > 旧start)则冲突。
     */
    private void checkConflict(SiteBooking booking, Long excludeId) {
        if (!StringUtils.hasText(booking.getSiteName()) || booking.getStartTime() == null || booking.getEndTime() == null) {
            throw new BizException("请选择场地及起止时间");
        }
        LocalDateTime start = booking.getStartTime();
        LocalDateTime end = booking.getEndTime();
        if (!start.isBefore(end)) {
            throw new BizException("结束时间必须晚于开始时间");
        }
        List<SiteBooking> existing = siteBookingMapper.selectList(
                new LambdaQueryWrapper<SiteBooking>()
                        .eq(SiteBooking::getSiteName, booking.getSiteName())
                        .ne(SiteBooking::getStatus, ST_CANCELLED));
        for (SiteBooking sb : existing) {
            if (excludeId != null && excludeId.equals(sb.getId())) {
                continue;
            }
            if (sb.getStartTime() == null || sb.getEndTime() == null) {
                continue;
            }
            if (start.isBefore(sb.getEndTime()) && end.isAfter(sb.getStartTime())) {
                throw new BizException("该时段场地已被预约");
            }
        }
    }
}
