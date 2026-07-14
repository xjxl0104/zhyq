package com.zhyq.park.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 领域事件默认消费者(批次① 地基):记录型 + Webhook 出口。
 *
 * <p>用 {@link TransactionalEventListener}(AFTER_COMMIT):仅当发布事件的业务事务成功提交后
 * 才消费,保证"事件所述之事确已落库"。当前只做两件无副作用的事——留痕日志、转发 Webhook 出口
 * (默认关闭)。批次② 的规则中心、SLA、催缴等真实消费者按同一模式各自新增监听器接入,互不影响。</p>
 *
 * <p>注:发布点均在 {@code @Transactional} 业务方法内(见 ContractService),故走 AFTER_COMMIT。
 * {@code fallbackExecution=true} 保证即使在无事务上下文发布也能被消费,不会被静默丢弃。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListener {

    private final WebhookDispatcher webhookDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDomainEvent(DomainEvent event) {
        log.info("[event] {} @ {} -> {}", event.type(), event.occurredAt(), event);
        webhookDispatcher.dispatch(event);
    }
}
