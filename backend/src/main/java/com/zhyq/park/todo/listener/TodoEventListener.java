package com.zhyq.park.todo.listener;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.todo.entity.Todo;
import com.zhyq.park.todo.mapper.TodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 领域事件 → 待办(批次② 待办承载)。
 *
 * <p>把关键领域事件落成 {@code sys_todo} 记录,让"提交审批""新工单待派单"等自动进入待办列表。
 * 纯消费方,不改 todo 现有接口;{@code owner} 暂填 "system"(无登录鉴权,真实指派待 #7 鉴权轮)。
 * 与批次① 的 {@code DomainEventListener} 并列,各自独立消费,互不影响。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoEventListener {

    private final TodoMapper todoMapper;

    private static final int STATUS_TODO = 1; // 待办

    /** 合同提交审批 → 生成一条审批待办。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContractSubmitted(DomainEvent.ContractSubmitted e) {
        Todo t = new Todo();
        t.setTitle("合同待审批:" + e.code());
        t.setBizType("approval");
        t.setBizId(e.contractId());
        t.setOwner("system");
        t.setDueDate(LocalDateTime.now().plusDays(1));
        t.setStatus(STATUS_TODO);
        todoMapper.insert(t);
    }

    /** 新工单 → 生成一条派单待办。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkOrderCreated(DomainEvent.WorkOrderCreated e) {
        Todo t = new Todo();
        t.setTitle("工单待派单" + (e.category() == null ? "" : ":" + e.category()));
        t.setBizType("workorder");
        t.setBizId(e.workOrderId());
        t.setOwner("system");
        t.setStatus(STATUS_TODO);
        todoMapper.insert(t);
    }

    /** 审批链新节点任务 → 生成一条审批待办(owner 为节点审批人约定值,#7 前多为角色码)。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkflowTaskCreated(DomainEvent.WorkflowTaskCreated e) {
        Todo t = new Todo();
        t.setTitle("审批待办:" + (e.nodeName() == null ? "审批节点" : e.nodeName()));
        t.setBizType("approval");
        t.setBizId(e.taskId());
        t.setOwner(e.assignee() == null ? "system" : e.assignee());
        t.setDueDate(LocalDateTime.now().plusDays(1));
        t.setStatus(STATUS_TODO);
        todoMapper.insert(t);
    }
}
