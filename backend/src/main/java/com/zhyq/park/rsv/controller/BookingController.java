package com.zhyq.park.rsv.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.rsv.entity.ResvBooking;
import com.zhyq.park.rsv.mapper.ResvBookingMapper;
import com.zhyq.park.rsv.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "资源预订-预订")
@RestController
@RequestMapping("/rsv/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final ResvBookingMapper bookingMapper;

    @Operation(summary = "分页查询预订")
    @GetMapping("/page")
    public Result<PageResult<ResvBooking>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) Long resourceId,
                                                @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<ResvBooking> qw = new LambdaQueryWrapper<>();
        qw.eq(resourceId != null, ResvBooking::getResourceId, resourceId)
          .eq(status != null, ResvBooking::getStatus, status)
          .orderByDesc(ResvBooking::getId);
        IPage<ResvBooking> p = bookingMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "预订详情")
    @GetMapping("/{id}")
    public Result<ResvBooking> get(@PathVariable Long id) {
        return Result.ok(bookingMapper.selectById(id));
    }

    @Operation(summary = "预订(时段冲突校验+计费,费用仅存不写账单)")
    @PostMapping
    public Result<Long> book(@RequestBody ResvBooking booking) {
        return Result.ok(bookingService.book(booking));
    }

    @Operation(summary = "取消预订")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        bookingService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "完成预订")
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        bookingService.finish(id);
        return Result.ok();
    }

    @Operation(summary = "查某资源日期范围内已订时段(供日历)")
    @GetMapping("/slots")
    public Result<List<ResvBooking>> slots(@RequestParam Long resourceId,
                                           @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
                                           @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        return Result.ok(bookingService.bookedSlots(resourceId, from, to));
    }
}
