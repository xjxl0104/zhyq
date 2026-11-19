package com.zhyq.park.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.iot.entity.Alarm;

public interface AlarmMapper extends BaseMapper<Alarm> {
    /** Older releases could lose the create-order action while keeping the alarm active. */
    @org.apache.ibatis.annotations.Select("""
            SELECT NOT EXISTS(SELECT 1 FROM iot_alarm_workorder_action WHERE alarm_id = #{id})
               AND NOT EXISTS(SELECT 1 FROM pm_work_order WHERE source_alarm_id = #{id})
            """)
    boolean missingWorkOrderAction(@org.apache.ibatis.annotations.Param("id") Long id);
}
