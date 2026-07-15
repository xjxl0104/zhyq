package com.zhyq.park.rule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.event.DomainEvent.AlarmRaised;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.property.service.WorkOrderService;
import com.zhyq.park.rule.entity.Rule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 规则动作执行器(#8)。
 *
 * <p>按 {@code Rule.actionType} 分派;首期仅 {@code CREATE_WORKORDER}——
 * 按空间派单创建工单,创建前按 {@code source_alarm_id} 查重(design §5 D4),防止同一告警反复建单。
 * 创建路径照抄 {@code WorkOrderController.add()}(insert + 发 WorkOrderCreated),不经 Service,
 * 与既有工单新增路径保持一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleActionExecutor {

    private static final String ACTION_CREATE_WORKORDER = "CREATE_WORKORDER";
    /** 派单人占位(D3:#7 鉴权轮后接真实指派) */
    private static final String PLACEHOLDER_ASSIGNEE = "system";

    private final WorkOrderMapper workOrderMapper;
    private final ApplicationEventPublisher eventPublisher;

    public void execute(Rule rule, AlarmRaised event) {
        if (ACTION_CREATE_WORKORDER.equals(rule.getActionType())) {
            createWorkOrder(rule, event);
        } else {
            log.warn("未知动作类型,跳过:actionType={}, ruleId={}", rule.getActionType(), rule.getId());
        }
    }

    private void createWorkOrder(Rule rule, AlarmRaised event) {
        Long alarmId = event.alarmId();
        long existing = workOrderMapper.selectCount(
                new LambdaQueryWrapper<WorkOrder>().eq(WorkOrder::getSourceAlarmId, alarmId));
        if (existing > 0) {
            log.info("同一告警已生成过工单,跳过防刷屏:alarmId={}, ruleId={}", alarmId, rule.getId());
            return;
        }

        JSONObject config = parseConfig(rule.getActionConfigJson());
        String orderType = config == null ? null : config.getStr("orderType");
        String title = config == null ? null : config.getStr("title");

        WorkOrder wo = new WorkOrder();
        wo.setCode("WO" + System.currentTimeMillis());
        wo.setOrderType(StrUtil.isBlank(orderType) ? "告警" : orderType);
        wo.setTitle(StrUtil.isBlank(title) ? "IoT告警工单" : title);
        wo.setSpaceId(event.spaceId());
        wo.setSourceAlarmId(alarmId);
        wo.setSource("规则引擎");
        wo.setStatus(WorkOrderService.ST_PENDING_DISPATCH);
        wo.setAssignee(PLACEHOLDER_ASSIGNEE);
        workOrderMapper.insert(wo);

        eventPublisher.publishEvent(new DomainEvent.WorkOrderCreated(
                wo.getId(), wo.getOrderType(), null, LocalDateTime.now()));
        log.info("规则自动建单成功:workOrderId={}, alarmId={}, ruleId={}", wo.getId(), alarmId, rule.getId());
    }

    private static JSONObject parseConfig(String actionConfigJson) {
        if (StrUtil.isBlank(actionConfigJson)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(actionConfigJson);
        } catch (JSONException e) {
            return null;
        }
    }
}
