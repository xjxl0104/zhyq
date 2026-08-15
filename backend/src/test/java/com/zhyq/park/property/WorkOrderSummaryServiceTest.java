package com.zhyq.park.property;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhyq.park.property.entity.WorkOrder;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.property.service.WorkOrderService;
import com.zhyq.park.property.service.WorkOrderSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 汇总口径测试。核心回归点:原 /stats 只统计状态 1/3/5,
 * 漏 2/4/6/7 导致各项之和不等于 total。
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderSummaryServiceTest {

    @Mock
    private WorkOrderMapper workOrderMapper;

    private WorkOrderSummaryService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderSummaryService(workOrderMapper);
    }

    private static WorkOrder wo(int status, String source, Integer slaState) {
        WorkOrder w = new WorkOrder();
        w.setStatus(status);
        w.setSource(source);
        w.setSlaState(slaState);
        w.setUrgency(2);
        w.setOrderType("报修");
        w.setCreateTime(LocalDateTime.now().minusDays(1));
        return w;
    }

    @Test
    void statsCoversEveryStatusSoPartsSumToTotal() {
        // 七种状态各一条,外加一条重复的处理中
        when(workOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                wo(WorkOrderService.ST_PENDING_DISPATCH, "手工", null),
                wo(WorkOrderService.ST_PENDING_ACCEPT, "手工", null),
                wo(WorkOrderService.ST_PROCESSING, "巡检计划", null),
                wo(WorkOrderService.ST_PROCESSING, "安防巡更", null),
                wo(WorkOrderService.ST_PENDING_VERIFY, "手工", null),
                wo(WorkOrderService.ST_DONE, "手工", null),
                wo(WorkOrderService.ST_CLOSED, "手工", null),
                wo(WorkOrderService.ST_TIMEOUT, "手工", null)));

        Map<String, Object> m = service.toStatsMap(service.countByStatus(null));

        assertEquals(8L, m.get("total"));
        assertEquals(1L, m.get("pendingDispatch"));
        assertEquals(1L, m.get("pendingAccept"));
        assertEquals(2L, m.get("processing"));
        assertEquals(1L, m.get("pendingVerify"));
        assertEquals(1L, m.get("done"));
        assertEquals(1L, m.get("closed"));
        assertEquals(1L, m.get("timeout"));

        // 关键断言:各状态之和必须等于 total(旧实现做不到)
        long sum = (long) m.get("pendingDispatch") + (long) m.get("pendingAccept")
                + (long) m.get("processing") + (long) m.get("pendingVerify")
                + (long) m.get("done") + (long) m.get("closed") + (long) m.get("timeout");
        assertEquals(m.get("total"), sum);

        // 在办 = 1+2+3+4 = 5
        assertEquals(5L, m.get("open"));
        assertEquals(m.get("open"), m.get("pending"), "pending 应与 open 同口径,避免首页与列表数字打架");
    }

    @Test
    void summaryGroupsBySourceAndFillsBlankAsUnlabeled() {
        when(workOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                wo(WorkOrderService.ST_PROCESSING, "巡检计划", null),
                wo(WorkOrderService.ST_PROCESSING, "巡检计划", null),
                wo(WorkOrderService.ST_DONE, null, null)));

        Map<String, Object> out = service.summary(null, 30);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bySource = (List<Map<String, Object>>) out.get("bySource");
        // 按数量倒序:巡检计划(2) 在前
        assertEquals("巡检计划", bySource.get(0).get("name"));
        assertEquals(2L, bySource.get(0).get("count"));
        assertEquals("未标注", bySource.get(1).get("name"), "source 为空应归入未标注而非丢弃");
    }

    @Test
    void slaRateOnlyCountsSettledOrders() {
        when(workOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                wo(WorkOrderService.ST_DONE, "手工", 0),      // 达成
                wo(WorkOrderService.ST_DONE, "手工", 2),      // 解决超时
                wo(WorkOrderService.ST_CLOSED, "手工", 0),    // 达成
                wo(WorkOrderService.ST_PROCESSING, "手工", 1) // 在办超时,不计入达成率
        ));

        Map<String, Object> out = service.summary(null, 30);
        @SuppressWarnings("unchecked")
        Map<String, Object> sla = (Map<String, Object>) out.get("sla");

        assertEquals(3L, sla.get("settled"), "在办单不应计入 settled");
        assertEquals(1L, sla.get("breached"));
        assertEquals(2L, sla.get("met"));
        assertEquals(66.7, sla.get("metRate"));
        assertEquals(1L, sla.get("openTimeout"), "在办且已超时的单需单独暴露以便干预");
    }

    @Test
    void slaRateIsNullWhenNothingSettled() {
        when(workOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                wo(WorkOrderService.ST_PROCESSING, "手工", null)));

        Map<String, Object> out = service.summary(null, 30);
        @SuppressWarnings("unchecked")
        Map<String, Object> sla = (Map<String, Object>) out.get("sla");

        assertEquals(0L, sla.get("settled"));
        assertNull(sla.get("metRate"), "无终态单时达成率应为 null 而不是 0,避免前端显示 0% 误导");
        assertNotNull(out.get("trend"));
    }
}
