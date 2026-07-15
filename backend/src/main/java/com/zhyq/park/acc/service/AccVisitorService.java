package com.zhyq.park.acc.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.acc.entity.AccVisitor;
import com.zhyq.park.acc.mapper.AccVisitorMapper;
import com.zhyq.park.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 访客预约/到访服务(#21)。状态机:1已预约 → 2已到访 → 3已离场;1 → 4已取消。
 *
 * <p><b>并发手法(沿用 #13/#20/#23):条件 UPDATE 抢状态</b> —— 用
 * {@code LambdaUpdateWrapper.eq(status, 期望前态)} 更新并校验受影响行数,
 * 而非 read-then-updateById(本项目曾因读-改-写有并发 bug)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccVisitorService {

    public static final int ST_RESERVED = 1; // 已预约
    public static final int ST_ARRIVED = 2;   // 已到访
    public static final int ST_LEFT = 3;      // 已离场
    public static final int ST_CANCELLED = 4; // 已取消

    private final AccVisitorMapper visitorMapper;

    /** 登记预约:status=1。 */
    @Transactional(rollbackFor = Exception.class)
    public Long reserve(AccVisitor visitor) {
        if (visitor.getName() == null || visitor.getName().isBlank()) {
            throw new BizException("访客姓名必填");
        }
        if (visitor.getVisitTime() == null) {
            throw new BizException("预约到访时间必填");
        }
        visitor.setId(null); // 防外部误传
        visitor.setStatus(ST_RESERVED);
        visitorMapper.insert(visitor);
        return visitor.getId();
    }

    /** 到访签到:1 → 2,记到访时间。 */
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long id) {
        transition(id, ST_RESERVED, ST_ARRIVED, "仅已预约状态可签到到访");
    }

    /** 离场:2 → 3。 */
    @Transactional(rollbackFor = Exception.class)
    public void leave(Long id) {
        transition(id, ST_ARRIVED, ST_LEFT, "仅已到访状态可离场");
    }

    /** 取消:1 → 4(已到访不可取消)。 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        transition(id, ST_RESERVED, ST_CANCELLED, "仅已预约状态可取消");
    }

    /**
     * 条件 UPDATE 抢状态:from → to,受影响 0 行说明前态不符(已被并发改动/状态非法),抛业务异常。
     */
    private void transition(Long id, int from, int to, String rejectMsg) {
        requireExists(id);
        LambdaUpdateWrapper<AccVisitor> uw = new LambdaUpdateWrapper<AccVisitor>()
                .eq(AccVisitor::getId, id)
                .eq(AccVisitor::getStatus, from)
                .set(AccVisitor::getStatus, to);
        int rows = visitorMapper.update(null, uw);
        if (rows == 0) {
            throw new BizException(rejectMsg);
        }
    }

    private void requireExists(Long id) {
        AccVisitor v = visitorMapper.selectById(id);
        if (v == null) {
            throw new BizException("访客记录不存在");
        }
    }
}
