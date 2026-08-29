package com.zhyq.park.budget.listener;

import com.zhyq.park.budget.service.BudgetService;
import com.zhyq.park.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 预算申请的审批链回调:消费 workflow 发出的通过/驳回事件,回写预算状态。
 *
 * <p>做法对齐合同与采购申请:用 AFTER_COMMIT,仅当审批链事务成功提交后才回写,
 * 保证「审批实例确已置为通过/驳回」。budget 单向依赖 workflow,不反向。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetWorkflowListener {

    private final BudgetService budgetService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApproved(DomainEvent.WorkflowApproved e) {
        if (BudgetService.BIZ_TYPE.equals(e.bizType())) {
            log.info("[budget] 审批链通过 → 预算置为已通过 budgetId={}", e.bizId());
            budgetService.onApproved(e.bizId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRejected(DomainEvent.WorkflowRejected e) {
        if (BudgetService.BIZ_TYPE.equals(e.bizType())) {
            log.info("[budget] 审批链驳回 → 预算置为已驳回 budgetId={}", e.bizId());
            budgetService.onRejected(e.bizId());
        }
    }
}
