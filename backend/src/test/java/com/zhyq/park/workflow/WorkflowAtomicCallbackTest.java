package com.zhyq.park.workflow;

import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.service.ContractService;
import com.zhyq.park.workflow.listener.WorkflowCallbackListener;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowAtomicCallbackTest {
    @Test void businessFailureRollsBackWorkflowBeforeAnyCommit() {
        var events=new ArrayList<String>();var manager=new RecordingTransactions(events);
        var contract=mock(ContractService.class);
        doAnswer(call->{events.add("business");throw new BizException("bill generation failed");}).when(contract).approve(7L);
        try(var context=context(contract)) {
            assertThatThrownBy(()->new TransactionTemplate(manager).executeWithoutResult(status -> {
                events.add("workflow");context.publishEvent(new DomainEvent.WorkflowApproved("contract",7L,LocalDateTime.now()));
            })).isInstanceOf(BizException.class);
            assertThat(events).containsExactly("workflow","business","rollback");
        }
    }
    @Test void successfulBusinessAndWorkflowCommitTogether() {
        var events=new ArrayList<String>();var manager=new RecordingTransactions(events);
        var contract=mock(ContractService.class);doAnswer(call->{events.add("business");return null;}).when(contract).approve(7L);
        try(var context=context(contract)) {
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                events.add("workflow");context.publishEvent(new DomainEvent.WorkflowApproved("contract",7L,LocalDateTime.now()));
            });
            assertThat(events).containsExactly("workflow","business","commit");
        }
    }
    private AnnotationConfigApplicationContext context(ContractService contract) {
        var context=new AnnotationConfigApplicationContext();
        context.registerBean(WorkflowCallbackListener.class,()->new WorkflowCallbackListener(contract));context.refresh();return context;
    }
    @Test void rejectionRestoresDraftBeforeWorkflowCommit() {
        var events=new ArrayList<String>();var manager=new RecordingTransactions(events);
        var contract=mock(ContractService.class);doAnswer(call->{events.add("draft");return null;}).when(contract).reject(7L);
        try(var context=context(contract)) {
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                events.add("rejected");context.publishEvent(new DomainEvent.WorkflowRejected("contract",7L,LocalDateTime.now()));
            });
            assertThat(events).containsExactly("rejected","draft","commit");
        }
    }
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        private final List<String> events;
        RecordingTransactions(List<String> events){this.events=events;}
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object transaction,TransactionDefinition definition){}
        protected void doCommit(DefaultTransactionStatus status){events.add("commit");}
        protected void doRollback(DefaultTransactionStatus status){events.add("rollback");}
    }
}
