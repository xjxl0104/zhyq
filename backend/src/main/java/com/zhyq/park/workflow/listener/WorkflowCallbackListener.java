package com.zhyq.park.workflow.listener;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.contract.service.ContractService;
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
public class WorkflowCallbackListener {

    private final ContractService contractService;

    private static final String BIZ_CONTRACT = "contract";

    @EventListener
    public void onWorkflowApproved(DomainEvent.WorkflowApproved e) {
        if (BIZ_CONTRACT.equals(e.bizType())) {
            log.info("[workflow] 审批链通过 → 触发合同审批 contractId={}", e.bizId());
            contractService.approve(e.bizId());
        } else {
            log.info("[workflow] 审批链通过,bizType={} 暂无业务回调,忽略 bizId={}", e.bizType(), e.bizId());
        }
    }

    @EventListener
    public void onWorkflowRejected(DomainEvent.WorkflowRejected e) {
        if (BIZ_CONTRACT.equals(e.bizType())) contractService.reject(e.bizId());
    }
}
