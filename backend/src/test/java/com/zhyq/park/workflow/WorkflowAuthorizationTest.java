package com.zhyq.park.workflow;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.workflow.entity.*;
import com.zhyq.park.workflow.mapper.*;
import com.zhyq.park.workflow.service.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowAuthorizationTest {
    WfNodeMapper nodes=mock(WfNodeMapper.class);
    WfTaskMapper tasks=mock(WfTaskMapper.class);
    WfInstanceMapper instances=mock(WfInstanceMapper.class);
    WfDefinitionMapper definitions=mock(WfDefinitionMapper.class);
    WorkflowBusinessMapper business=mock(WorkflowBusinessMapper.class);
    ApplicationEventPublisher events=mock(ApplicationEventPublisher.class);
    WorkflowAccessService access=new WorkflowAccessService(nodes,tasks,business,definitions,instances);
    WorkflowService workflow=new WorkflowService(definitions,nodes,instances,tasks,events,access);
    WfTask task; WfNode node; WfInstance instance;
    @BeforeEach void setup() {
        for(Class<?> type:List.of(WfNode.class,WfTask.class,WfInstance.class,WfDefinition.class,com.zhyq.park.oa.entity.Approval.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"test"),type);
        node=new WfNode();node.setId(5L);node.setApproverType("user");node.setApproverValue("assigned");node.setSeq(1);
        task=new WfTask();task.setId(7L);task.setNodeId(5L);task.setInstanceId(9L);task.setSeq(1);task.setAssignee("assigned");task.setStatus(1);
        instance=new WfInstance();instance.setId(9L);instance.setDefinitionId(3L);instance.setBizType("budget");instance.setBizId(20L);instance.setCurrentSeq(1);instance.setStatus(1);
        when(nodes.selectHistoricalApprovalNode(5L)).thenReturn(node);
        when(tasks.selectById(7L)).thenReturn(task);when(instances.selectById(9L)).thenReturn(instance);
        when(tasks.update(isNull(),any())).thenReturn(1);when(instances.update(isNull(),any())).thenReturn(1);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void unassignedUserCannotActEvenWithWorkflowConfigurationPermission(){
        login("intruder","workflow:definition:manage","budget:query");
        denied(()->workflow.approve(7L,"forged"));denied(()->workflow.reject(7L,"forged"));
        verify(tasks,never()).update(isNull(),any());verify(instances,never()).update(isNull(),any());verifyNoInteractions(events);
    }
    @Test void assignedUserCanApproveWithoutUnrelatedBusinessRoles(){
        login("assigned");workflow.approve(7L,"ok");
        verify(events).publishEvent(argThat((Object e)->e instanceof DomainEvent.WorkflowApproved));
    }
    @Test void assignedRoleCanRejectButSameNamedUsernameCannotImpersonateRole(){
        node.setApproverType("role");task.setAssignee("finance");
        login("finance");denied(()->workflow.reject(7L,"forged"));
        login("accountant","ROLE_finance");workflow.reject(7L,"ok");
        verify(events).publishEvent(argThat((Object e)->e instanceof DomainEvent.WorkflowRejected));
    }
    @Test void roleMatchingUsernameDoesNotGrantUserNodeAndAdminRemainsAvailable(){
        login("intruder","ROLE_assigned");denied(()->workflow.approve(7L,null));
        login("admin","ROLE_admin");workflow.approve(7L,"admin recovery");
    }
    @Test void staleTaskCannotChangeAnotherCurrentNode(){
        instance.setCurrentSeq(2);login("assigned");
        assertThatThrownBy(()->workflow.approve(7L,null)).hasMessageContaining("当前审批节点");
        verify(tasks,never()).update(isNull(),any());
    }
    @Test void pendingQueryUsesCallerIdentityAndRoleInsteadOfClientAssignee(){
        login("alice","ROLE_finance");denied(()->workflow.myTasks("someone-else"));
        workflow.myTasks("alice");verify(tasks).selectAuthorizedPending("alice",List.of("finance"),false,null);
    }
    @Test void instanceQueryRequiresBusinessReadOrParticipationBeforePaging(){
        login("intruder");denied(()->access.scopeInstances(new LambdaQueryWrapper<>()));denied(()->access.requireVisible(instance));
        login("reader","budget:query");LambdaQueryWrapper<WfInstance> q=new LambdaQueryWrapper<>();access.scopeInstances(q);q.getSqlSegment();
        assertThat(q.getParamNameValuePairs().values()).contains("budget").doesNotContain("contract","procurement");access.requireVisible(instance);
        login("assignee");when(tasks.selectParticipatingInstanceIds("assignee",List.of())).thenReturn(List.of(9L));
        q=new LambdaQueryWrapper<>();access.scopeInstances(q);q.getSqlSegment();assertThat(q.getParamNameValuePairs().values()).contains(9L);
        access.requireVisible(instance);instance.setId(10L);denied(()->access.requireVisible(instance));
    }
    @Test void initiatorWithoutBusinessQueryCanSeeOnlyTheirOwnInstances(){
        login("submitter","budget:submit");instance.setCreateBy("submitter");
        when(tasks.selectParticipatingInstanceIds("submitter",List.of())).thenReturn(List.of(9L));
        LambdaQueryWrapper<WfInstance> q=new LambdaQueryWrapper<>();access.scopeInstances(q);q.getSqlSegment();
        assertThat(q.getParamNameValuePairs().values()).containsExactly(9L);access.requireVisible(instance);
    }
    @Test void startRequiresSubmitPermissionBeforeBusinessWrites(){
        login("intruder","workflow:definition:manage");denied(()->workflow.start("budget",20L,null));
        verifyNoInteractions(business);verify(definitions,never()).selectOne(any());verify(instances,never()).insert(any(WfInstance.class));
    }
    @Test void startupRejectsDraftBusinessAndForeignApprovalHeader(){
        login("submitter","budget:submit");when(business.lockBudgetStatus(20L)).thenReturn(1);
        assertThatThrownBy(()->workflow.start("budget",20L)).hasMessageContaining("业务单据的提交审批");
        when(business.lockBudgetStatus(20L)).thenReturn(2);denied(()->workflow.start("budget",20L,99L));
        verify(instances,never()).insert(any(WfInstance.class));
    }
    @Test void repeatedStartLocksBusinessThenReturnsExistingRunningInstance(){
        login("submitter","budget:submit");when(business.lockBudgetStatus(20L)).thenReturn(2);
        when(instances.selectOne(any())).thenReturn(instance);assertThat(workflow.start("budget",20L)).isEqualTo(9L);
        verify(business).lockBudgetStatus(20L);verify(instances,never()).insert(any(WfInstance.class));
    }
    @Test void manualApprovalCannotSkipConfiguredOrRunningChainEvenForAdmin(){
        login("ordinary");denied(()->access.requireDirectApproval("contract",20L));
        login("operator","contract:approve");when(definitions.countEnabledWithNodes("contract")).thenReturn(1);
        assertThatThrownBy(()->access.requireDirectApproval("contract",20L)).hasMessageContaining("不能跳过");
        login("admin","ROLE_admin");assertThatThrownBy(()->access.requireDirectApproval("contract",20L)).hasMessageContaining("不能跳过");
        when(definitions.countEnabledWithNodes("contract")).thenReturn(0);when(instances.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(()->access.requireDirectApproval("contract",20L)).hasMessageContaining("不能跳过");
        when(instances.selectCount(any())).thenReturn(0L);access.requireDirectApproval("contract",20L);
    }
    @Test void headerFailurePreventsBusinessCallbackAndSuccessRecordsActor(){
        login("assigned");instance.setApprovalId(11L);
        when(business.completeApproval(11L,"budget",20L,3,"assigned","ok")).thenReturn(0);
        assertThatThrownBy(()->workflow.approve(7L,"ok")).hasMessageContaining("关联审批单状态");verifyNoInteractions(events);
        when(business.completeApproval(11L,"budget",20L,3,"assigned","ok")).thenReturn(1);workflow.approve(7L,"ok");
        verify(events).publishEvent(argThat((Object e)->e instanceof DomainEvent.WorkflowApproved));
    }
    @Test void unknownNodeKindsFailBeforeReplacingConfiguration(){
        node.setApproverType("dept");assertThatThrownBy(()->access.validateNodes(List.of(node))).hasMessageContaining("用户或角色");
    }
    private void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action){
        assertThatThrownBy(action).isInstanceOf(BizException.class).extracting(e->((BizException)e).getCode()).isEqualTo(403);
    }
    private void login(String name,String... permissions){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name,null,Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));}
}
