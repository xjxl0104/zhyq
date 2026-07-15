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
    @Transactional(rollbackFor = Exception.class)
    public Long raise(Alarm alarm) {
        LocalDateTime now = LocalDateTime.now();
        Alarm existedActive = alarmMapper.selectOne(new LambdaQueryWrapper<Alarm>()
                .eq(Alarm::getDeviceId, alarm.getDeviceId())
                .eq(Alarm::getAlarmType, alarm.getAlarmType())
                .eq(Alarm::getActive, ACTIVE)
                .last("limit 1"));
        if (existedActive != null) {
            increment(existedActive, now);
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
            if (dup != null) {
                increment(dup, now);
                return dup.getId();
            }
            throw e;
        }

        publishAlarmRaised(alarm, now);
        return alarm.getId();
    }

    private void increment(Alarm existed, LocalDateTime now) {
        Alarm upd = new Alarm();
        upd.setId(existed.getId());
        upd.setOccurCount((existed.getOccurCount() == null ? 1 : existed.getOccurCount()) + 1);
        upd.setLastTime(now);
        alarmMapper.updateById(upd);
    }

    private void publishAlarmRaised(Alarm alarm, LocalDateTime now) {
        try {
            Long spaceId = null;
            if (alarm.getDeviceId() != null) {
                Device device = deviceMapper.selectById(alarm.getDeviceId());
                if (device != null) {
                    spaceId = device.getSpaceId();
                }
            }
            eventPublisher.publishEvent(new DomainEvent.AlarmRaised(
                    alarm.getId(), alarm.getDeviceId(),
                    alarm.getLevel() == null ? null : String.valueOf(alarm.getLevel()),
                    spaceId, alarm.getAlarmType(), now));
        } catch (Exception e) {
            log.warn("发布 AlarmRaised 事件失败,不影响告警落库,alarmId={}", alarm.getId(), e);
        }
    }

    /** 确认:新建(1)→已确认(2) */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        Alarm alarm = require(id);
        if (alarm.getStatus() == null || alarm.getStatus() != ST_NEW) {
            throw new BizException("仅新建状态的告警可确认");
        }
        Alarm upd = new Alarm();
        upd.setId(id);
        upd.setStatus(ST_CONFIRMED);
        alarmMapper.updateById(upd);
    }

    /** 开始处理:已确认(2)→处理中(3),可带受理人 */
    @Transactional(rollbackFor = Exception.class)
    public void start(Long id, String assignee) {
        Alarm alarm = require(id);
        if (alarm.getStatus() == null || alarm.getStatus() != ST_CONFIRMED) {
            throw new BizException("仅已确认状态的告警可开始处理");
        }
        Alarm upd = new Alarm();
        upd.setId(id);
        upd.setStatus(ST_PROCESSING);
        if (StringUtils.hasText(assignee)) {
            upd.setAssignee(assignee);
        }
        alarmMapper.updateById(upd);
    }

    /** 恢复:处理中(3)→已恢复(4),退出活动域(active=NULL) */
    @Transactional(rollbackFor = Exception.class)
    public void recover(Long id) {
        Alarm alarm = require(id);
        if (alarm.getStatus() == null || alarm.getStatus() != ST_PROCESSING) {
            throw new BizException("仅处理中状态的告警可标记恢复");
        }
        clearActiveAndSetStatus(id, ST_RECOVERED);
    }

    /** 关闭:→已关闭(5),退出活动域(active=NULL)。保留既有对外语义,额外置 active=NULL */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        require(id);
        clearActiveAndSetStatus(id, ST_CLOSED);
    }

    /** 误报:→误报(6),退出活动域(active=NULL) */
    @Transactional(rollbackFor = Exception.class)
    public void falsePositive(Long id) {
        require(id);
        clearActiveAndSetStatus(id, ST_FALSE_POSITIVE);
    }

    /**
     * updateById 对 null 字段默认跳过(NOT_NULL 策略),不会清空 active 列,
     * 因此用 LambdaUpdateWrapper 显式 {@code set(Alarm::getActive, null)} 一次性写 status+active。
     */
    private void clearActiveAndSetStatus(Long id, int status) {
        alarmMapper.update(null, new LambdaUpdateWrapper<Alarm>()
                .eq(Alarm::getId, id)
                .set(Alarm::getStatus, status)
                .set(Alarm::getActive, null));
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
