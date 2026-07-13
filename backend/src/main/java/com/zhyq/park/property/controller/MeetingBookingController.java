package com.zhyq.park.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.property.entity.MeetingBooking;
import com.zhyq.park.property.mapper.MeetingBookingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "物业-会议室预约")
@RestController
@RequestMapping("/property/meeting/booking")
@RequiredArgsConstructor
public class MeetingBookingController {

    private final MeetingBookingMapper meetingBookingMapper;

    private static final int ST_CANCELLED = 3; // 已取消
    private static final int ST_REFUNDED = 4;   // 已退款

    @Operation(summary = "分页查询会议室预约")
    @GetMapping("/page")
    public Result<PageResult<MeetingBooking>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                   @RequestParam(defaultValue = "10") int pageSize,
                                                   @RequestParam(required = false) Long roomId,
                                                   @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<MeetingBooking> qw = new LambdaQueryWrapper<>();
        qw.eq(roomId != null, MeetingBooking::getRoomId, roomId)
          .eq(status != null, MeetingBooking::getStatus, status)
          .orderByDesc(MeetingBooking::getId);
        IPage<MeetingBooking> p = meetingBookingMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "预约详情")
    @GetMapping("/{id}")
    public Result<MeetingBooking> get(@PathVariable Long id) {
        return Result.ok(meetingBookingMapper.selectById(id));
    }

    @Operation(summary = "新增预约(校验时间冲突)")
    @PostMapping
    public Result<Long> add(@RequestBody MeetingBooking booking) {
        checkConflict(booking, null);
        meetingBookingMapper.insert(booking);
        return Result.ok(booking.getId());
    }

    @Operation(summary = "修改预约(校验时间冲突)")
    @PutMapping
    public Result<Void> update(@RequestBody MeetingBooking booking) {
        checkConflict(booking, booking.getId());
        meetingBookingMapper.updateById(booking);
        return Result.ok();
    }

    @Operation(summary = "删除预约")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        meetingBookingMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 时间冲突校验:同一 roomId,存在未取消/未退款的预约,且时间段重叠(start < 已有end 且 end > 已有start)则冲突。
     */
    private void checkConflict(MeetingBooking booking, Long excludeId) {
        if (booking.getRoomId() == null || booking.getStartTime() == null || booking.getEndTime() == null) {
            throw new BizException("请选择会议室及起止时间");
        }
        LocalDateTime start = booking.getStartTime();
        LocalDateTime end = booking.getEndTime();
        if (!start.isBefore(end)) {
            throw new BizException("结束时间必须晚于开始时间");
        }
        List<MeetingBooking> existing = meetingBookingMapper.selectList(
                new LambdaQueryWrapper<MeetingBooking>()
                        .eq(MeetingBooking::getRoomId, booking.getRoomId())
                        .notIn(MeetingBooking::getStatus, ST_CANCELLED, ST_REFUNDED));
        for (MeetingBooking mb : existing) {
            if (excludeId != null && excludeId.equals(mb.getId())) {
                continue;
            }
            if (mb.getStartTime() == null || mb.getEndTime() == null) {
                continue;
            }
            // 区间重叠:新start < 旧end 且 新end > 旧start
            if (start.isBefore(mb.getEndTime()) && end.isAfter(mb.getStartTime())) {
                throw new BizException("该时段已被预约");
            }
        }
    }
}
