package com.zhyq.park.rsv.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.rsv.entity.ResvBooking;
import com.zhyq.park.rsv.entity.ResvResource;
import com.zhyq.park.rsv.mapper.ResvBookingMapper;
import com.zhyq.park.rsv.mapper.ResvResourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一预订服务(#23):时段冲突校验 + 计费(仅存预订记录)+ 状态流转。
 *
 * <p>状态:1已预订 2已取消 3已完成。</p>
 *
 * <p><b>费用边界(设计 §5 D1):</b>fee 只在预订记录上算并存,<b>绝不写 fin_bill / 触发收款</b>。
 * 本服务不注入、不引用任何 finance 组件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    public static final int ST_BOOKED = 1;    // 已预订
    public static final int ST_CANCELLED = 2; // 已取消
    public static final int ST_FINISHED = 3;  // 已完成

    private final ResvBookingMapper bookingMapper;
    private final ResvResourceMapper resourceMapper;

    /**
     * 预订:时段冲突校验(同 resourceId、status=已预订、时段重叠)→ 冲突拒绝;
     * 计费 fee=ceil(时长小时)×price_per_hour(price 空则 fee 空);insert status=1。
     *
     * <p>冲突校验 + insert 在同一事务内。注意:无 DB 唯一约束兜底,极端并发下两个请求可能
     * 都通过校验(校验查不到对方未提交的插入)→ 可能重复,属可接受(设计 D2:事务内查重防并发,
     * 非强原子)。如需绝对防重,后续可加数据库排他约束或串行化隔离。</p>
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long book(ResvBooking booking) {
        if (booking.getResourceId() == null || booking.getStartTime() == null || booking.getEndTime() == null) {
            throw new BizException("请选择资源及起止时间");
        }
        LocalDateTime start = booking.getStartTime();
        LocalDateTime end = booking.getEndTime();
        if (!start.isBefore(end)) {
            throw new BizException("结束时间必须晚于开始时间");
        }

        ResvResource resource = resourceMapper.selectById(booking.getResourceId());
        if (resource == null) {
            throw new BizException("资源不存在");
        }
        if (resource.getStatus() != null && resource.getStatus() == 0) {
            throw new BizException("资源已停用,不可预订");
        }

        checkConflict(booking.getResourceId(), start, end, null);

        long minutes = Duration.between(start, end).toMinutes();
        booking.setFee(BookingPolicy.calcFee(minutes, resource.getPricePerHour()));
        booking.setStatus(ST_BOOKED);
        booking.setId(null); // 防外部误传
        bookingMapper.insert(booking);
        return booking.getId();
    }

    /** 取消:→已取消(2),释放时段 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ResvBooking booking = require(id);
        if (booking.getStatus() != null && booking.getStatus() == ST_CANCELLED) {
            throw new BizException("该预订已取消");
        }
        ResvBooking upd = new ResvBooking();
        upd.setId(id);
        upd.setStatus(ST_CANCELLED);
        bookingMapper.updateById(upd);
    }

    /** 完成:→已完成(3) */
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id) {
        ResvBooking booking = require(id);
        if (booking.getStatus() == null || booking.getStatus() != ST_BOOKED) {
            throw new BizException("仅已预订状态可标记完成");
        }
        ResvBooking upd = new ResvBooking();
        upd.setId(id);
        upd.setStatus(ST_FINISHED);
        bookingMapper.updateById(upd);
    }

    /** 查某资源在 [from,to) 内已占用(status=已预订)的时段,供日历渲染 */
    public List<ResvBooking> bookedSlots(Long resourceId, LocalDateTime from, LocalDateTime to) {
        if (resourceId == null || from == null || to == null) {
            throw new BizException("请提供资源与日期范围");
        }
        return bookingMapper.selectList(new LambdaQueryWrapper<ResvBooking>()
                .eq(ResvBooking::getResourceId, resourceId)
                .eq(ResvBooking::getStatus, ST_BOOKED)
                // 与 [from,to) 重叠:start < to 且 end > from
                .lt(ResvBooking::getStartTime, to)
                .gt(ResvBooking::getEndTime, from)
                .orderByAsc(ResvBooking::getStartTime));
    }

    private ResvBooking require(Long id) {
        ResvBooking booking = bookingMapper.selectById(id);
        if (booking == null) {
            throw new BizException("预订不存在");
        }
        return booking;
    }

    /**
     * 时段冲突校验:同 resourceId、status=已预订、区间重叠(新start < 旧end 且 新end > 旧start)则拒。
     */
    private void checkConflict(Long resourceId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        List<ResvBooking> existing = bookingMapper.selectList(new LambdaQueryWrapper<ResvBooking>()
                .eq(ResvBooking::getResourceId, resourceId)
                .eq(ResvBooking::getStatus, ST_BOOKED));
        for (ResvBooking b : existing) {
            if (excludeId != null && excludeId.equals(b.getId())) {
                continue;
            }
            if (b.getStartTime() == null || b.getEndTime() == null) {
                continue;
            }
            if (start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime())) {
                throw new BizException("该时段已被预订");
            }
        }
    }
}
