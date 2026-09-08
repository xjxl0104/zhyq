package com.zhyq.park.pur.controller;

import com.zhyq.park.pur.entity.PurPlan;
import com.zhyq.park.pur.mapper.PurPlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * 采购计划新增的服务端赋值约束。
 *
 * <p>由来:{@code pur_plan.plan_no} 是 NOT NULL 且无默认值,而新增表单里没有这一项,
 * 控制器此前也不生成 —— 每次新增都以
 * {@code SQLException: Field 'plan_no' doesn't have a default value} 失败,
 * 前端只看得到"系统繁忙,请稍后重试"。本测试锁住编号必须由服务端生成。</p>
 */
@ExtendWith(MockitoExtension.class)
class PurPlanControllerTest {

    @Mock private PurPlanMapper planMapper;

    private PurPlanController controller;

    @BeforeEach
    void setUp() {
        controller = new PurPlanController(planMapper);
    }

    @Test
    void addGeneratesPlanNoBeforeInsert() {
        controller.add(newPlan());

        ArgumentCaptor<PurPlan> captor = ArgumentCaptor.forClass(PurPlan.class);
        verify(planMapper).insert(captor.capture());
        String no = captor.getValue().getPlanNo();

        assertNotNull(no, "plan_no 是 NOT NULL 列,必须由服务端生成");
        assertTrue(no.matches("PP-\\d{8}-\\d{3}"), "编号形制应为 PP-yyyyMMdd-NNN,实际: " + no);
        assertTrue(no.contains(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))),
                "编号应含当天日期: " + no);
    }

    /** 入参里带 planNo 也不作数:编号归服务端,避免前端伪造或撞唯一键。 */
    @Test
    void addIgnoresClientSuppliedPlanNo() {
        PurPlan plan = newPlan();
        plan.setPlanNo("PP-伪造-001");

        controller.add(plan);

        ArgumentCaptor<PurPlan> captor = ArgumentCaptor.forClass(PurPlan.class);
        verify(planMapper).insert(captor.capture());
        assertTrue(captor.getValue().getPlanNo().matches("PP-\\d{8}-\\d{3}"),
                "客户端传入的编号应被服务端覆盖");
    }

    /** 与上游 a5b4198 的加固口径一致:状态强制草稿、租户不接受入参。 */
    @Test
    void addForcesDraftStatusAndClearsTenant() {
        PurPlan plan = newPlan();
        plan.setStatus(3);
        plan.setTenantId(999L);

        controller.add(plan);

        ArgumentCaptor<PurPlan> captor = ArgumentCaptor.forClass(PurPlan.class);
        verify(planMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        assertNull(captor.getValue().getTenantId());
    }

    /** 唯一键 uk_plan_no 之下,连续新增不该轻易撞号。 */
    @Test
    void generatedPlanNosAreDistinctAcrossCalls() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(PurPlanController.genPlanNo());
        }
        assertTrue(seen.size() > 150,
                "200 次生成的编号重复过多(去重后仅 " + seen.size() + " 个),撞唯一键风险偏高");
    }

    private PurPlan newPlan() {
        PurPlan plan = new PurPlan();
        plan.setTitle("2026年9月物业维护采购计划");
        plan.setPlanType(2);
        plan.setPeriod("2026-09");
        plan.setBudgetAmount(new BigDecimal("4.00"));
        return plan;
    }
}
