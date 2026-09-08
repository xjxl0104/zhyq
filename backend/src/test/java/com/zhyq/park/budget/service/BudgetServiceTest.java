package com.zhyq.park.budget.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.budget.entity.Budget;
import com.zhyq.park.budget.mapper.BudgetMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.workflow.service.WorkflowService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private BudgetMapper budgetMapper;
    @Mock private WorkflowService workflowService;

    private BudgetService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "budget-test"), Budget.class);
        service = new BudgetService(budgetMapper, workflowService);
    }

    @Test
    void createRejectsUnknownBudgetType() {
        Budget budget = new Budget();
        budget.setTitle("2026年度预算");
        budget.setBudgetType(9);

        assertThrows(BizException.class, () -> service.create(budget));
        verify(budgetMapper, never()).insert(any(Budget.class));
    }

    @Test
    void createRejectsNegativeAmount() {
        Budget budget = annual();
        budget.setAmount(new BigDecimal("-1"));

        assertThrows(BizException.class, () -> service.create(budget));
        verify(budgetMapper, never()).insert(any(Budget.class));
    }

    @Test
    void createStartsAsDraftWithGeneratedNo() {
        Budget budget = annual();

        service.create(budget);

        assertEquals(BudgetService.ST_DRAFT, budget.getStatus());
        assertEquals("BG-", budget.getBudgetNo().substring(0, 3));
        verify(budgetMapper).insert(budget);
    }

    /** 状态流转是条件更新:前态不合法时 update 影响 0 行,必须抛异常且不发起审批链。 */
    @Test
    void submitThrowsAndSkipsWorkflowWhenStateIsNotSubmittable() {
        when(budgetMapper.update(any(), any())).thenReturn(0);

        assertThrows(BizException.class, () -> service.submit(7L));

        verify(workflowService, never()).start(any(), any());
    }

    @Test
    void submitStartsWorkflowOnBudgetBizType() {
        when(budgetMapper.update(any(), any())).thenReturn(1);

        service.submit(7L);

        verify(workflowService).start(eq(BudgetService.BIZ_TYPE), eq(7L));
    }

    @Test
    void archiveThrowsWhenNotApproved() {
        when(budgetMapper.update(any(), any())).thenReturn(0);

        assertThrows(BizException.class, () -> service.archive(7L));
    }

    @Test
    void removeIsBlockedForInFlightBudget() {
        Budget budget = annual();
        budget.setId(7L);
        budget.setStatus(BudgetService.ST_AUDITING);
        when(budgetMapper.selectById(7L)).thenReturn(budget);

        assertThrows(BizException.class, () -> service.remove(7L));

        verify(budgetMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void removeDeletesDraft() {
        Budget budget = annual();
        budget.setId(7L);
        budget.setStatus(BudgetService.ST_DRAFT);
        when(budgetMapper.selectById(7L)).thenReturn(budget);

        service.remove(7L);

        verify(budgetMapper).deleteById(7L);
    }

    @Test
    void detailThrowsWhenMissing() {
        when(budgetMapper.selectById(404L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.detail(404L));
    }

    private Budget annual() {
        Budget budget = new Budget();
        budget.setTitle("2026年度园区运营总预算");
        budget.setBudgetType(BudgetService.TYPE_ANNUAL);
        budget.setPeriod("2026");
        budget.setAmount(new BigDecimal("1860000.00"));
        return budget;
    }
}
