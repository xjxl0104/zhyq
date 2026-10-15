package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErpReconciliationServiceTest {
    @Mock MktWarehouseMapper warehouses;
    @Mock MktReferralOrderMapper orders;
    @Mock ErpReconciliationQueryMapper queries;
    @Mock MktCustomerErpMapMapper mappings;
    @Mock MktErpReconcileSnapshotMapper snapshots;
    @Mock MktAuditService audit;
    ErpReconciliationService service;
    static final LocalDate START = LocalDate.of(2026, 9, 1), END = LocalDate.of(2026, 9, 30);

    @BeforeAll static void tableInfo() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (var type : List.of(MktWarehouse.class, MktReferralOrder.class, MktErpEventInbox.class,
                MktErpUnmapped.class, MktCustomerErpMap.class, MktErpReconcileSnapshot.class)) TableInfoHelper.initTableInfo(assistant, type);
    }
    @BeforeEach void setup() {
        service = new ErpReconciliationService(warehouses, orders, queries, mappings, snapshots, audit, new ObjectMapper());
        var wh = new MktWarehouse(); wh.setId(7L); wh.setOrderMode("erp"); wh.setProjectId(1L);
        lenient().when(warehouses.selectOne(any())).thenReturn(wh);
        lenient().when(orders.selectList(any())).thenReturn(List.of());
        lenient().when(queries.periodEvents(anyLong(), any(), any())).thenReturn(List.of());
        lenient().when(mappings.selectList(any())).thenReturn(List.of());
        lenient().when(queries.periodUnmapped(anyLong(), any(), any())).thenReturn(List.of());
        lenient().doAnswer(call -> { call.getArgument(0, MktErpReconcileSnapshot.class).setId(100L); return 1; })
                .when(snapshots).insert(any(MktErpReconcileSnapshot.class));
    }

    @Test void externalEvidenceIsRequiredEvenWhenBothCountsAreZero() {
        var input = new ErpReconciliationService.Submission(START, END, 0L, "ERP", " ");
        assertThatThrownBy(() -> service.submit(7L, input)).isInstanceOf(BizException.class).hasMessageContaining("凭据");
        verify(snapshots, never()).insert(any(MktErpReconcileSnapshot.class));
    }
    @Test void oneOrderDifferenceCannotBeWaivedByConfirmationReason() {
        when(orders.selectList(any())).thenReturn(List.of(order(1L, 2)));
        var draft = draft(2);
        when(snapshots.selectById(9L)).thenReturn(draft);
        when(snapshots.selectOne(any())).thenReturn(draft);
        assertThatThrownBy(() -> service.confirm(7L, 9L, "愿意承担差异", "admin"))
                .isInstanceOf(BizException.class).hasMessageContaining("单量差异");
        verify(snapshots, never()).insert(any(MktErpReconcileSnapshot.class));
    }
    @Test void unresolvedCustomerBlocksAnOtherwiseEqualPeriod() {
        var event = event("WAITING_MAPPING"); when(queries.periodEvents(anyLong(), any(), any())).thenReturn(List.of(event));
        var draft = draft(0); when(snapshots.selectById(9L)).thenReturn(draft); when(snapshots.selectOne(any())).thenReturn(draft);
        assertThat(service.calculate(7L, START, END).unmappedCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.confirm(7L, 9L, "已复核", "admin")).isInstanceOf(BizException.class).hasMessageContaining("待处理事件");
    }
    @Test void retryingCancellationBlocksEvenIfCountHasNotChanged() {
        var event = event("RETRY"); event.setEvent("order.cancelled"); when(queries.periodEvents(anyLong(), any(), any())).thenReturn(List.of(event));
        var result = service.calculate(7L, START, END);
        assertThat(result.systemOrderCount()).isZero(); assertThat(result.retryCount()).isEqualTo(1);
        assertThat(result.blockingCount()).isEqualTo(1);
    }
    @Test void confirmationWritesANewVersionAndPreservesDraftNumbers() {
        var draft = draft(0); when(snapshots.selectById(9L)).thenReturn(draft); when(snapshots.selectOne(any())).thenReturn(draft);
        var confirmed = service.confirm(7L, 9L, "已核对 ERP 导出客户筛选与日期", "admin");
        assertThat(confirmed.getState()).isEqualTo("CONFIRMED"); assertThat(confirmed.getRevision()).isEqualTo(2);
        assertThat(confirmed.getPreviousSnapshotId()).isEqualTo(9L); assertThat(confirmed.getConfirmedBy()).isEqualTo("admin");
        assertThat(draft.getState()).isEqualTo("DRAFT"); assertThat(draft.getRevision()).isEqualTo(1);
        assertThat(confirmed.getExpectedAmount()).isZero(); assertThat(confirmed.getSystemOrderCount()).isZero();
    }
    @Test void mappingChangesInvalidateEvenEqualCounts() {
        var draft = draft(0); when(snapshots.selectById(9L)).thenReturn(draft); when(snapshots.selectOne(any())).thenReturn(draft);
        var mapping = new MktCustomerErpMap(); mapping.setId(1L); mapping.setWarehouseId(7L); mapping.setCustomerCode("A"); mapping.setCustomerId(22L); mapping.setStatus(1);
        when(mappings.selectList(any())).thenReturn(List.of(mapping));
        assertThatThrownBy(() -> service.confirm(7L, 9L, "已复核", "admin")).isInstanceOf(BizException.class).hasMessageContaining("已变化");
    }
    @Test void refreshAppendsDraftWithoutEditingAlreadyConfirmedEvidence() {
        var confirmed = draft(0); confirmed.setState("CONFIRMED"); when(snapshots.selectOne(any())).thenReturn(confirmed);
        when(orders.selectList(any())).thenReturn(List.of(order(1L, 2)));
        service.refreshPeriod(7L, START, END);
        var capture = ArgumentCaptor.forClass(MktErpReconcileSnapshot.class); verify(snapshots).insert(capture.capture());
        assertThat(capture.getValue().getState()).isEqualTo("DRAFT"); assertThat(capture.getValue().getSystemOrderCount()).isEqualTo(1);
        assertThat(capture.getValue().getExternalOrderCount()).isZero(); assertThat(capture.getValue().getDiffOrderCount()).isEqualTo(-1);
        assertThat(confirmed.getState()).isEqualTo("CONFIRMED"); verify(snapshots, never()).updateById(any(MktErpReconcileSnapshot.class));
    }
    @Test void latestUnconfirmedReportBlocksUseOfAnOlderConfirmation() {
        var unconfirmed = draft(0);
        when(snapshots.selectOne(any())).thenReturn(unconfirmed);
        assertThatThrownBy(() -> service.requireConfirmed(7L, START, END)).isInstanceOf(BizException.class).hasMessageContaining("尚未确认");
    }
    @Test void countIncludesManualSupplementButExcludesCancelledAndRefundedOrders() {
        when(orders.selectList(any())).thenReturn(List.of(order(1L, 2), order(2L, 3), order(3L, 4)));
        var result = service.calculate(7L, START, END);
        assertThat(result.systemOrderCount()).isEqualTo(1); assertThat(result.cancelledOrderCount()).isEqualTo(2);
    }
    @Test void snapshotCannotBeConfirmedByAnotherWarehouse() {
        var snapshot = draft(0); snapshot.setWarehouseId(8L); when(snapshots.selectById(9L)).thenReturn(snapshot);
        assertThatThrownBy(() -> service.confirm(7L, 9L, "已复核", "admin")).isInstanceOf(BizException.class).hasMessageContaining("不属于");
    }
    private MktErpReconcileSnapshot draft(long external) {
        var s = new MktErpReconcileSnapshot(); s.setId(9L); s.setWarehouseId(7L); s.setPeriodStart(START); s.setPeriodEnd(END);
        s.setMetric(ErpReconciliationService.METRIC); s.setState("DRAFT"); s.setRevision(1); s.setExternalOrderCount(external);
        s.setSourceName("外部 ERP 出库报表"); s.setEvidence("erp-202609.xlsx，筛选营销货主，排除取消退款");
        s.setDataFingerprint(service.calculate(7L, START, END).fingerprint()); return s;
    }
    private static MktReferralOrder order(long id, int status) {
        var order = new MktReferralOrder(); order.setId(id); order.setWarehouseId(7L); order.setSourceNo("OUT-" + id);
        order.setStatus(status); order.setEventTime(START.atStartOfDay()); order.setQty(10); order.setPackages(2); return order;
    }
    private static MktErpEventInbox event(String status) {
        var e = new MktErpEventInbox(); e.setId(3L); e.setEvent("order.shipped"); e.setEventId("E1"); e.setOrderNo("OUT-1"); e.setStatus(status); return e;
    }
}
