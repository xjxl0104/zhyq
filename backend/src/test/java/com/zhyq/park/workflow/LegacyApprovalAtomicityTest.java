package com.zhyq.park.workflow;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.contract.mapper.ContractMapper;
import com.zhyq.park.contract.service.ContractService;
import com.zhyq.park.oa.controller.ApprovalController;
import com.zhyq.park.oa.entity.Approval;
import com.zhyq.park.oa.mapper.ApprovalMapper;
import com.zhyq.park.workflow.service.WorkflowAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class LegacyApprovalAtomicityTest {
    @Configuration @EnableTransactionManagement static class Transactions {}
    @Test void businessFailureRollsBackLegacyApprovalInsteadOfBeingSwallowed(){
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), Approval.class);
        var approvals=mock(ApprovalMapper.class);var contracts=mock(ContractService.class);
        var contractMapper=mock(ContractMapper.class);var access=mock(WorkflowAccessService.class);var tx=new TransactionsRecorder();
        Approval a=new Approval();a.setId(1L);a.setBizType("contract");a.setBizId(7L);a.setStatus(2);
        when(approvals.selectById(1L)).thenReturn(a);when(approvals.update(isNull(),any())).thenReturn(1);
        doThrow(new BizException("bill validation failed")).when(contracts).approve(7L);
        try(var context=new AnnotationConfigApplicationContext()){
            context.register(Transactions.class);context.registerBean("transactionManager",TransactionsRecorder.class,()->tx);
            context.registerBean(ApprovalController.class,()->new ApprovalController(approvals,contracts,contractMapper,access));context.refresh();
            assertThatThrownBy(()->context.getBean(ApprovalController.class).approve(1L,Map.of("opinion","test")))
                    .isInstanceOf(BizException.class).hasMessageContaining("bill validation failed");
            assertThat(tx.rollbacks).isEqualTo(1);assertThat(tx.commits).isZero();verify(access).requireDirectApproval("contract",7L);
        }
    }
    static class TransactionsRecorder extends AbstractPlatformTransactionManager {
        int commits;int rollbacks;
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object t,TransactionDefinition d){}
        protected void doCommit(DefaultTransactionStatus s){commits++;}
        protected void doRollback(DefaultTransactionStatus s){rollbacks++;}
    }
}
