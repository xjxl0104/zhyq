package com.zhyq.park.pur.listener;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.pur.service.PurRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 采购申请的审批链回调:消费 workflow 发出的通过/驳回事件,回写采购申请状态。
 *
 * <p>做法对齐合同模块的 WorkflowCallbackListener:用 AFTER_COMMIT,仅当审批链事务成功提交后才回写,
 * 保证「审批实例确已置为通过/驳回」。pur 单向依赖 workflow,不反向。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurWorkflowListener {

    private final PurRequestService requestService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApproved(DomainEvent.WorkflowApproved e) {
        if (PurRequestService.BIZ_TYPE.equals(e.bizType())) {
            log.info("[pur] 审批链通过 → 采购申请置为已通过 requestId={}", e.bizId());
            requestService.onApproved(e.bizId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRejected(DomainEvent.WorkflowRejected e) {
        if (PurRequestService.BIZ_TYPE.equals(e.bizType())) {
            log.info("[pur] 审批链驳回 → 采购申请置为已驳回 requestId={}", e.bizId());
            requestService.onRejected(e.bizId());
        }
    }
}
