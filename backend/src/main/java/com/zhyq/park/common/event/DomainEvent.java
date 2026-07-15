package com.zhyq.park.common.event;

import java.time.LocalDateTime;

/**
 * 领域事件最小集(批次① 地基)。
 *
 * <p>用 sealed interface + 嵌套 record 承载约 10 个领域事件:一处集中定义、类型安全、
 * 监听器可跨模块引用。发布点由各业务服务通过 {@code ApplicationEventPublisher} 逐步接入,
 * 消费方见 {@link DomainEventListener}(记录型)与 Webhook 出口。</p>
 *
 * <p><b>本轮为保守引入:仅新增"发布事件"这一步,不改动任何既有业务逻辑与事务边界。</b>
 * 真正把副作用(如锁房源、生成账单)迁移为事件驱动,留待批次② 规则中心并先行审设计。</p>
 */
public sealed interface DomainEvent {

    /** 事件发生业务时刻(由发布方传入,便于 Webhook/审计留痕) */
    LocalDateTime occurredAt();

    /** 事件类型标识,用于日志、Webhook 路由、前端订阅 */
    String type();

    // ==================== 合同域 ====================

    /** 合同审批通过(执行中)。当前锁房源+生成账单仍在 ContractService 同事务内完成,本事件仅供下游感知。 */
    record ContractApproved(Long contractId, String code, Long tenantRefId, Long projectId,
                            LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "contract.approved"; }
    }

    /** 合同退租(已终止)。 */
    record ContractTerminated(Long contractId, String code, Long tenantRefId, Long projectId,
                              LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "contract.terminated"; }
    }

    /** 合同提交审批(待审核)。 */
    record ContractSubmitted(Long contractId, String code, LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "contract.submitted"; }
    }

    // ==================== 财务域 ====================

    /** 账单生成(按合同计划批量出账后汇总)。 */
    record BillGenerated(Long contractId, Long projectId, int billCount, LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "bill.generated"; }
    }

    /** 收款到账。 */
    record PaymentReceived(Long billId, Long contractId, LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "payment.received"; }
    }

    /** 账单逾期(供催缴任务流消费,批次②)。 */
    record BillOverdue(Long billId, Long contractId, Long tenantRefId, LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "bill.overdue"; }
    }

    // ==================== 物业 / 工单域 ====================

    /** 工单创建(供 SLA、智能派单钩子消费)。 */
    record WorkOrderCreated(Long workOrderId, String category, Long roomId, LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "workorder.created"; }
    }

    /** 工单关闭/完成(供回访评价闭环消费)。 */
    record WorkOrderClosed(Long workOrderId, LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "workorder.closed"; }
    }

    // ==================== 物联 / 预警域 ====================

    /** IoT 告警产生(供联动规则中心消费:自动生成工单并按空间派单,批次②;#13 加 alarmType 供规则按告警类型匹配)。 */
    record AlarmRaised(Long alarmId, Long deviceId, String level, Long spaceId, String alarmType,
                       LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "alarm.raised"; }
    }

    // ==================== 招商域 ====================

    /** 招商线索创建(供商机管道消费,批次④)。 */
    record LeadCreated(Long leadId, String source, LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "lead.created"; }
    }

    // ==================== 工作流 / 审批链域(批次②) ====================

    /** 审批链末节点通过 → 审批实例整体通过。回调监听器据此驱动业务动作(如 contract → contractService.approve)。 */
    record WorkflowApproved(String bizType, Long bizId, LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "workflow.approved"; }
    }

    /** 审批链任一节点驳回 → 审批实例整体驳回。 */
    record WorkflowRejected(String bizType, Long bizId, LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "workflow.rejected"; }
    }

    /** 审批链新到一个节点、生成待审任务(→ TodoEventListener 建审批待办)。 */
    record WorkflowTaskCreated(String bizType, Long bizId, Long taskId, String nodeName, String assignee,
                               LocalDateTime occurredAt) implements DomainEvent {
        public String type() { return "workflow.task.created"; }
    }
}
