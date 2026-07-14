package com.zhyq.park.workflow.listener;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.contract.service.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 审批链业务回调(设计文档 §4.4)。消费 {@link DomainEvent.WorkflowApproved},按 bizType 分发到业务动作。
 *
 * <p>当前只接 {@code contract → contractService.approve(bizId)}:审批链末节点通过后,才真正触发
 * 合同的锁房源+生成账单。<b>ContractService.approve() 内部逻辑一字不动</b>,本监听器只是换了触发来源
 * (原来是人点 ApprovalController.approve,现在是审批链末节点通过后的事件回调)。</p>
 *
 * <p>用 AFTER_COMMIT:仅当 WorkflowService 的通过事务成功提交后才回调,保证"实例确已置为通过"。
 * contractService.approve 自身开新事务(其内部 @Transactional),回调失败不影响审批链已提交的状态;
 * 最坏情况摘掉本监听器即回到"审批链不驱动业务"的安全态(设计文档 §5 回退方案3)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowCallbackListener {

    private final ContractService contractService;

    private static final String BIZ_CONTRACT = "contract";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkflowApproved(DomainEvent.WorkflowApproved e) {
        if (BIZ_CONTRACT.equals(e.bizType())) {
            log.info("[workflow] 审批链通过 → 触发合同审批 contractId={}", e.bizId());
            contractService.approve(e.bizId());
        } else {
            log.info("[workflow] 审批链通过,bizType={} 暂无业务回调,忽略 bizId={}", e.bizType(), e.bizId());
        }
    }
}
