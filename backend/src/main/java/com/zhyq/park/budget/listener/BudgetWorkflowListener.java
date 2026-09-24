package com.zhyq.park.budget.listener;

import com.zhyq.park.budget.service.BudgetService;
import com.zhyq.park.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

/**
 * 审批业务在工作流提交前参与同一事务。业务失败时任务、实例和业务变更一起回滚，
 * 保留原待审核状态供重试；提交后的通知仍由独立 AFTER_COMMIT 监听器负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetWorkflowListener {

    private final BudgetService budgetService;

    @EventListener
    public void onApproved(DomainEvent.WorkflowApproved e) {
        if (BudgetService.BIZ_TYPE.equals(e.bizType())) {
            log.info("[budget] 审批链通过 → 预算置为已通过 budgetId={}", e.bizId());
            budgetService.onApproved(e.bizId());
        }
    }

    @EventListener
    public void onRejected(DomainEvent.WorkflowRejected e) {
        if (BudgetService.BIZ_TYPE.equals(e.bizType())) {
            log.info("[budget] 审批链驳回 → 预算置为已驳回 budgetId={}", e.bizId());
            budgetService.onRejected(e.bizId());
        }
    }
}
