package com.zhyq.park.pur.listener;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.pur.service.PurRequestService;
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
public class PurWorkflowListener {

    private final PurRequestService requestService;

    @EventListener
    public void onApproved(DomainEvent.WorkflowApproved e) {
        if (PurRequestService.BIZ_TYPE.equals(e.bizType())) {
            log.info("[pur] 审批链通过 → 采购申请置为已通过 requestId={}", e.bizId());
            requestService.onApproved(e.bizId());
        }
    }

    @EventListener
    public void onRejected(DomainEvent.WorkflowRejected e) {
        if (PurRequestService.BIZ_TYPE.equals(e.bizType())) {
            log.info("[pur] 审批链驳回 → 采购申请置为已驳回 requestId={}", e.bizId());
            requestService.onRejected(e.bizId());
        }
    }
}
