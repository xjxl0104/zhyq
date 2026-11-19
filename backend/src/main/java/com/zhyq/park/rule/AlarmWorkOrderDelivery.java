package com.zhyq.park.rule;

import com.zhyq.park.rule.entity.Rule;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlarmWorkOrderDelivery {
    private final JdbcTemplate jdbc;
    private final RuleActionExecutor executor;

    private record Action(long ruleId, String config, Long spaceId) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long alarmId) {
        var actions = jdbc.query("""
                SELECT rule_id, action_config, space_id FROM iot_alarm_workorder_action
                WHERE alarm_id=? AND completed_at IS NULL AND next_attempt<=NOW() FOR UPDATE
                """, (rs, i) -> new Action(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class)), alarmId);
        if (actions.isEmpty()) return;
        Action action = actions.get(0);
        Rule rule = new Rule();
        rule.setId(action.ruleId());
        rule.setActionConfigJson(action.config());
        executor.createWorkOrder(rule, alarmId, action.spaceId());
        jdbc.update("UPDATE iot_alarm_workorder_action SET completed_at=NOW() WHERE alarm_id=?", alarmId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void defer(Long alarmId) {
        jdbc.update("UPDATE iot_alarm_workorder_action SET next_attempt=DATE_ADD(NOW(), INTERVAL 30 SECOND) WHERE alarm_id=? AND completed_at IS NULL", alarmId);
    }
}
