package com.zhyq.park.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.iot.entity.Alarm;
import com.zhyq.park.iot.entity.Device;
import com.zhyq.park.iot.mapper.AlarmMapper;
import com.zhyq.park.iot.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * IoT 告警生命周期(#13):去重上报(activeAlarm 唯一) + 状态机中间态 + 指派。
 *
 * <p>状态机:1新建 2已确认 3处理中 4已恢复 5已关闭 6误报。
 * active 标记:1=活动告警,进入 4/5/6 任一终态即置 NULL(退出 uk_active 唯一域,见 V23)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private static final int ST_NEW = 1;         // 新建
    private static final int ST_CONFIRMED = 2;   // 已确认
    private static final int ST_PROCESSING = 3;  // 处理中
    private static final int ST_RECOVERED = 4;   // 已恢复
    private static final int ST_CLOSED = 5;      // 已关闭
    private static final int ST_FALSE_POSITIVE = 6; // 误报
    private static final int ACTIVE = 1;

    private final AlarmMapper alarmMapper;
    private final DeviceMapper deviceMapper;
    private final ApplicationEventPublisher eventPublisher;

    private Alarm require(Long id) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BizException("告警不存在");
        }
        return alarm;
    }

    /**
     * 上报告警:同设备+同类型已有活动告警(active=1)则累加次数+更新最后时间,不发事件(防重复建单);
     * 否则新建活动告警并发 {@link DomainEvent.AlarmRaised}(供 #8 规则引擎消费建单)。
     * 唯一键 uk_active(device_id, alarm_type, active) 兜并发:insert 撞键则退化为累加路径。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long raise(Alarm alarm) {
        LocalDateTime now = LocalDateTime.now();
        Alarm existedActive = alarmMapper.selectOne(new LambdaQueryWrapper<Alarm>()
                .eq(Alarm::getDeviceId, alarm.getDeviceId())
                .eq(Alarm::getAlarmType, alarm.getAlarmType())
                .eq(Alarm::getActive, ACTIVE)
                .last("limit 1"));
        if (existedActive != null && increment(existedActive, now)) {
            return existedActive.getId();
        }

        alarm.setActive(ACTIVE);
        alarm.setFirstTime(now);
        alarm.setLastTime(now);
        alarm.setOccurCount(1);
        if (alarm.getStatus() == null) {
            alarm.setStatus(ST_NEW);
        }
        if (alarm.getAlarmTime() == null) {
            alarm.setAlarmTime(now);
        }
        try {
            alarmMapper.insert(alarm);
        } catch (DuplicateKeyException e) {
            // 并发下同 (deviceId, alarmType, active=1) 撞唯一键:说明已被并发请求建为活动告警,回查累加
            Alarm dup = alarmMapper.selectOne(new LambdaQueryWrapper<Alarm>()
                    .eq(Alarm::getDeviceId, alarm.getDeviceId())
                    .eq(Alarm::getAlarmType, alarm.getAlarmType())
                    .eq(Alarm::getActive, ACTIVE)
                    .last("limit 1"));
            if (dup != null && increment(dup, now)) {
                return dup.getId();
            }
            throw e;
        }

        publishAlarmRaised(alarm, now);
        return alarm.getId();
    }

    private boolean increment(Alarm existed, LocalDateTime now) {
        // 原地 read-modify-write 在并发重复上报下会丢更新,改为原子 SQL 自增(occur_count = occur_count + 1)
        return alarmMapper.update(null, new LambdaUpdateWrapper<Alarm>()
                .eq(Alarm::getId, existed.getId()).eq(Alarm::getActive, ACTIVE)
                .setSql("occur_count = occur_count + 1")
                .set(Alarm::getLastTime, now)) == 1;
    }

    private void publishAlarmRaised(Alarm alarm, LocalDateTime now) {
        Device device = alarm.getDeviceId() == null ? null : deviceMapper.selectById(alarm.getDeviceId());
        eventPublisher.publishEvent(new DomainEvent.AlarmRaised(
                alarm.getId(), alarm.getDeviceId(), alarm.getLevel() == null ? null : alarm.getLevel().toString(),
                device == null ? null : device.getSpaceId(), alarm.getAlarmType(), now));
    }

    /** 确认:新建(1)→已确认(2) */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        Alarm alarm = require(id);
        if (alarm.getStatus() == null || alarm.getStatus() != ST_NEW) {
            throw new BizException("仅新建状态的告警可确认");
        }
        transition(id, ST_NEW, ST_CONFIRMED, null);
    }

    /** 开始处理:已确认(2)→处理中(3),可带受理人 */
    @Transactional(rollbackFor = Exception.class)
    public void start(Long id, String assignee) {
        Alarm alarm = require(id);
        if (alarm.getStatus() == null || alarm.getStatus() != ST_CONFIRMED) {
            throw new BizException("仅已确认状态的告警可开始处理");
        }
        transition(id, ST_CONFIRMED, ST_PROCESSING, assignee);
    }

    private void transition(Long id, int from, int to, String assignee) {
        int changed = alarmMapper.update(null, new LambdaUpdateWrapper<Alarm>()
                .eq(Alarm::getId, id).eq(Alarm::getStatus, from).eq(Alarm::getActive, ACTIVE)
                .set(Alarm::getStatus, to).set(StringUtils.hasText(assignee), Alarm::getAssignee, assignee));
        if (changed != 1) throw new BizException("告警状态已变化，请刷新后重试");
    }

    /** 恢复:处理中(3)→已恢复(4),退出活动域(active=NULL) */
    @Transactional(rollbackFor = Exception.class)
    public void recover(Long id) {
        Alarm alarm = require(id);
        if (alarm.getStatus() == null || alarm.getStatus() != ST_PROCESSING) {
            throw new BizException("仅处理中状态的告警可标记恢复");
        }
        clearActiveAndSetStatus(id, ST_RECOVERED, ST_PROCESSING);
    }

    /** 关闭:→已关闭(5),退出活动域(active=NULL)。保留既有对外语义,额外置 active=NULL */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        var current = require(id);
        if (Integer.valueOf(ST_CLOSED).equals(current.getStatus())) return;
        finishFromSnapshot(current, ST_CLOSED);
    }

    /** 误报:→误报(6),退出活动域(active=NULL) */
    @Transactional(rollbackFor = Exception.class)
    public void falsePositive(Long id) {
        var current = require(id);
        if (Integer.valueOf(ST_FALSE_POSITIVE).equals(current.getStatus())) return;
        finishFromSnapshot(current, ST_FALSE_POSITIVE);
    }

    /**
     * updateById 对 null 字段默认跳过(NOT_NULL 策略),不会清空 active 列,
     * 因此用 LambdaUpdateWrapper 显式 {@code set(Alarm::getActive, null)} 一次性写 status+active。
     */
    private void clearActiveAndSetStatus(Long id, int status, Integer... allowed) {
        int changed = alarmMapper.update(null, new LambdaUpdateWrapper<Alarm>()
                .eq(Alarm::getId, id)
                .in(Alarm::getStatus, (Object[]) allowed).eq(Alarm::getActive, ACTIVE)
                .set(Alarm::getStatus, status).set(Alarm::getActive, null));
        if (changed != 1) throw new BizException("告警已结束或状态已变化，请刷新后重试");
    }

    private void finishFromSnapshot(Alarm alarm, int status) {
        var change = new LambdaUpdateWrapper<Alarm>().eq(Alarm::getId, alarm.getId())
                .eq(Alarm::getStatus, alarm.getStatus())
                .isNull(alarm.getActive() == null, Alarm::getActive)
                .eq(alarm.getActive() != null, Alarm::getActive, alarm.getActive())
                .set(Alarm::getStatus, status).set(Alarm::getActive, null);
        if (alarmMapper.update(null, change) != 1) throw new BizException("告警状态已变化，请刷新后重试");
    }

    /** 指派受理人 */
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, String assignee) {
        require(id);
        if (!StringUtils.hasText(assignee)) {
            throw new BizException("请指定受理人");
        }
        Alarm upd = new Alarm();
        upd.setId(id);
        upd.setAssignee(assignee);
        alarmMapper.updateById(upd);
    }
}
