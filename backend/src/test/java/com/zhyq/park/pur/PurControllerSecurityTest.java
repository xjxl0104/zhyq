package com.zhyq.park.pur;

import com.zhyq.park.pur.controller.PurPlanController;
import com.zhyq.park.pur.controller.PurRequestController;
import com.zhyq.park.pur.entity.PurPlan;
import com.zhyq.park.pur.entity.PurRequest;
import com.zhyq.park.pur.mapper.PurPlanMapper;
import com.zhyq.park.pur.mapper.PurRequestMapper;
import com.zhyq.park.pur.service.PurRequestService;
import com.zhyq.park.workflow.controller.WorkflowController;
import com.zhyq.park.workflow.entity.WfDefinition;
import com.zhyq.park.workflow.mapper.WfDefinitionMapper;
import com.zhyq.park.workflow.mapper.WfInstanceMapper;
import com.zhyq.park.workflow.mapper.WfNodeMapper;
import com.zhyq.park.workflow.mapper.WfTaskMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 采购模块 + 审批链定义配置的方法级权限边界。
 *
 * PR #4 原始提交 18 个接口全部无 @PreAuthorize,任何登录用户皆可调,
 * 其中审批链定义接口可改「谁能审批」实现自审。本测试锁住修复后的边界,
 * 防止后续再退回无注解状态。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PurControllerSecurityTest.TestBeans.class)
class PurControllerSecurityTest {

    @EnableMethodSecurity
    @Configuration
    static class TestBeans {
        @Bean PurPlanMapper planMapper() { return mock(PurPlanMapper.class); }
        @Bean PurRequestMapper requestMapper() { return mock(PurRequestMapper.class); }
        @Bean PurRequestService requestService() { return mock(PurRequestService.class); }
        @Bean WorkflowService workflowService() { return mock(WorkflowService.class); }
        @Bean WfDefinitionMapper definitionMapper() { return mock(WfDefinitionMapper.class); }
        @Bean WfNodeMapper nodeMapper() { return mock(WfNodeMapper.class); }
        @Bean WfInstanceMapper instanceMapper() { return mock(WfInstanceMapper.class); }
        @Bean WfTaskMapper taskMapper() { return mock(WfTaskMapper.class); }

        @Bean PurPlanController planController(PurPlanMapper m) {
            return new PurPlanController(m);
        }
        @Bean PurRequestController requestController(PurRequestMapper m, PurRequestService s) {
            return new PurRequestController(m, s);
        }
        @Bean WorkflowController workflowController(WorkflowService ws, WfDefinitionMapper d,
                                                    WfNodeMapper n, WfInstanceMapper i,
                                                    WfTaskMapper t) {
            return new WorkflowController(ws, d, n, i, t);
        }
    }

    @Autowired private PurPlanController planController;
    @Autowired private PurRequestController requestController;
    @Autowired private WorkflowController workflowController;
    @Autowired private PurPlanMapper planMapper;
    @Autowired private PurRequestMapper requestMapper;
    @Autowired private WfDefinitionMapper definitionMapper;

    /**
     * 分页接口拿到 mock 的 null IPage 会 NPE, 那会掩盖"是否被权限拦下"这件事
     * (NPE 和 AccessDeniedException 都能让 assertDoesNotThrow 失败)。
     * 这里给个空页, 让通过鉴权的调用能正常走完。
     */
    @BeforeEach
    void stubPaging() {
        when(planMapper.selectPage(any(), any())).thenReturn(new Page<>());
        when(requestMapper.selectPage(any(), any())).thenReturn(new Page<>());
        when(definitionMapper.selectPage(any(), any())).thenReturn(new Page<>());
    }

    /** 无关权限:采购全部接口应一律拒绝(修复前这里全部放行)。 */
    @Test
    @WithMockUser(authorities = "finance:bill:query")
    void unrelatedPermissionCannotTouchProcurement() {
        assertThrows(AccessDeniedException.class,
                () -> planController.page(1, 10, null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> planController.list(null));
        assertThrows(AccessDeniedException.class, () -> planController.get(1L));
        assertThrows(AccessDeniedException.class, () -> planController.add(new PurPlan()));
        assertThrows(AccessDeniedException.class, () -> planController.update(new PurPlan()));
        assertThrows(AccessDeniedException.class, () -> planController.remove(1L));

        assertThrows(AccessDeniedException.class,
                () -> requestController.page(1, 10, null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> requestController.get(1L));
        assertThrows(AccessDeniedException.class, () -> requestController.add(new PurRequest()));
        assertThrows(AccessDeniedException.class, () -> requestController.update(new PurRequest()));
        assertThrows(AccessDeniedException.class, () -> requestController.submit(1L));
        assertThrows(AccessDeniedException.class, () -> requestController.complete(1L));
        assertThrows(AccessDeniedException.class, () -> requestController.cancel(1L));
        assertThrows(AccessDeniedException.class, () -> requestController.remove(1L));
    }

    /** 查询权限只能查,不能写。 */
    @Test
    @WithMockUser(authorities = {"pur:plan:query", "pur:request:query"})
    void queryPermissionCannotWrite() {
        assertDoesNotThrow(() -> planController.page(1, 10, null, null, null, null));
        assertDoesNotThrow(() -> planController.get(1L));
        assertDoesNotThrow(() -> requestController.page(1, 10, null, null, null, null));

        assertThrows(AccessDeniedException.class, () -> planController.add(new PurPlan()));
        assertThrows(AccessDeniedException.class, () -> planController.remove(1L));
        assertThrows(AccessDeniedException.class, () -> requestController.submit(1L));
    }

    /** 提交审批与标记完成是独立权限位,不被 edit 覆盖。 */
    @Test
    @WithMockUser(authorities = "pur:request:edit")
    void editPermissionDoesNotImplySubmitOrComplete() {
        assertDoesNotThrow(() -> requestController.update(new PurRequest()));
        assertThrows(AccessDeniedException.class, () -> requestController.submit(1L));
        assertThrows(AccessDeniedException.class, () -> requestController.complete(1L));
        assertThrows(AccessDeniedException.class, () -> requestController.cancel(1L));
    }

    /**
     * 核心用例:采购权限最全的用户也不能碰审批链定义。
     * 否则申请人可把审批节点的审批人改成自己,自审自己的采购单。
     */
    @Test
    @WithMockUser(authorities = {"pur:plan:add", "pur:plan:edit", "pur:plan:delete",
            "pur:request:add", "pur:request:edit", "pur:request:submit",
            "pur:request:complete", "pur:request:cancel", "pur:request:delete"})
    void fullProcurementPermissionsCannotRewriteApprovalChain() {
        assertThrows(AccessDeniedException.class,
                () -> workflowController.saveNodes(1L, List.of()));
        assertThrows(AccessDeniedException.class,
                () -> workflowController.addDefinition(new WfDefinition()));
        assertThrows(AccessDeniedException.class,
                () -> workflowController.updateDefinition(new WfDefinition()));
        assertThrows(AccessDeniedException.class,
                () -> workflowController.removeDefinition(1L));
        assertThrows(AccessDeniedException.class,
                () -> workflowController.definitionPage(1, 10, null, null));
        assertThrows(AccessDeniedException.class, () -> workflowController.nodes(1L));
    }

    /** 持有配置权限位才能读写审批链定义。 */
    @Test
    @WithMockUser(authorities = "workflow:definition:manage")
    void definitionManagePermissionAllowsChainConfig() {
        assertDoesNotThrow(() -> workflowController.definitionPage(1, 10, null, null));
        assertDoesNotThrow(() -> workflowController.nodes(1L));
        assertDoesNotThrow(() -> workflowController.saveNodes(1L, List.of()));
        // 反向:配置权限不该顺带获得采购业务权限
        assertThrows(AccessDeniedException.class, () -> requestController.submit(1L));
    }
}
