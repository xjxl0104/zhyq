package com.zhyq.park.marketing.listener;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.marketing.service.MktWithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 伙伴提现审批流回调:与审批动作同一事务。末节点通过 → 提现单置为已审批(待财务打款);
 * 任一节点驳回 → 提现单驳回并释放锁定的佣金。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MktWithdrawalWorkflowListener {
    private final MktWithdrawalService withdrawals;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @EventListener
    public void onApproved(DomainEvent.WorkflowApproved e) {
        if (!MktWithdrawalService.WORKFLOW_BIZ_TYPE.equals(e.bizType())) return;
        log.info("[mkt] 提现审批通过 withdrawalId={}", e.bizId());
        withdrawals.onWorkflowApproved(e.bizId());
    }

    @EventListener
    public void onRejected(DomainEvent.WorkflowRejected e) {
        if (!MktWithdrawalService.WORKFLOW_BIZ_TYPE.equals(e.bizType())) return;
        log.info("[mkt] 提现审批驳回 withdrawalId={}", e.bizId());
        // 驳回意见写在审批任务上,同一事务内可读到
        String opinion = jdbc.query("SELECT t.opinion FROM wf_task t JOIN wf_instance i ON i.id = t.instance_id "
                + "WHERE i.biz_type = ? AND i.biz_id = ? AND t.status = 3 AND t.deleted = 0 ORDER BY t.id DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, e.bizType(), e.bizId());
        withdrawals.onWorkflowRejected(e.bizId(), opinion);
    }
}
