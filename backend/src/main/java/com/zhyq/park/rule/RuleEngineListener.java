package com.zhyq.park.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.event.DomainEvent.AlarmRaised;
import com.zhyq.park.rule.entity.Rule;
import com.zhyq.park.rule.mapper.RuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngineListener {
    private final RuleMapper rules;
    private final JdbcTemplate jdbc;
    private final AlarmWorkOrderDelivery delivery;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAlarmRaised(AlarmRaised event) {
        var candidates = rules.selectList(new LambdaQueryWrapper<Rule>()
                .eq(Rule::getTriggerType, "ALARM_RAISED").eq(Rule::getEnabled, 1)
                .eq(Rule::getActionType, "CREATE_WORKORDER").orderByAsc(Rule::getPriority, Rule::getId));
        for (Rule rule : candidates) {
            if (RuleEvaluator.matches(rule.getConditionJson(), event)) {
                // Each alarm can create one work order. Freeze the first matching rule as part of the alarm transaction.
                jdbc.update("""
                        INSERT INTO iot_alarm_workorder_action(alarm_id, rule_id, action_config, space_id)
                        VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE alarm_id=alarm_id
                        """, event.alarmId(), rule.getId(), rule.getActionConfigJson(), event.spaceId());
                break;
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(AlarmRaised event) {
        attempt(event.alarmId());
    }

    @Scheduled(fixedDelayString = "${zhyq.iot.rule-retry-ms:30000}")
    public void retry() {
        var due = jdbc.queryForList("""
                SELECT alarm_id FROM iot_alarm_workorder_action
                WHERE completed_at IS NULL AND next_attempt <= NOW()
                ORDER BY next_attempt, alarm_id LIMIT 100
                """, Long.class);
        due.forEach(this::attempt);
    }

    private void attempt(Long id) {
        try {
            delivery.deliver(id);
        } catch (RuntimeException error) {
            try { delivery.defer(id); }
            catch (RuntimeException retryError) { error.addSuppressed(retryError); }
            log.warn("自动建单将在稍后重试 alarmId={}", id, error);
        }
    }
}
