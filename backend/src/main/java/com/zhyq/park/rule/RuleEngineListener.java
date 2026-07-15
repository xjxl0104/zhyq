package com.zhyq.park.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.event.DomainEvent.AlarmRaised;
import com.zhyq.park.rule.entity.Rule;
import com.zhyq.park.rule.mapper.RuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 事件-联动规则引擎入口(#8)。
 *
 * <p>消费 {@link AlarmRaised},查启用中的 ALARM_RAISED 规则(按 priority 升序),
 * 逐条用 {@link RuleEvaluator} 判定是否命中,命中则交给 {@link RuleActionExecutor} 执行动作。
 * 遵循项目既有 {@code @TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)} 消费模式
 * (见 TodoEventListener / WorkflowCallbackListener),即"告警确已落库才跑规则"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngineListener {

    private static final String TRIGGER_ALARM_RAISED = "ALARM_RAISED";
    private static final int ENABLED = 1;

    private final RuleMapper ruleMapper;
    private final RuleActionExecutor ruleActionExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlarmRaised(AlarmRaised event) {
        List<Rule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<Rule>()
                        .eq(Rule::getTriggerType, TRIGGER_ALARM_RAISED)
                        .eq(Rule::getEnabled, ENABLED)
                        .orderByAsc(Rule::getPriority));
        for (Rule rule : rules) {
            try {
                if (!RuleEvaluator.matches(rule.getConditionJson(), event)) {
                    continue;
                }
                log.info("规则命中:ruleId={}, name={}, alarmId={}, actionType={}",
                        rule.getId(), rule.getName(), event.alarmId(), rule.getActionType());
                ruleActionExecutor.execute(rule, event);
            } catch (Exception e) {
                log.warn("规则执行异常,跳过该规则,不影响其余规则:ruleId={}, alarmId={}",
                        rule.getId(), event.alarmId(), e);
            }
        }
    }
}
