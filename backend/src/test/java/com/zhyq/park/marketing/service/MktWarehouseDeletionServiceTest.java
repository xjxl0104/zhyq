package com.zhyq.park.marketing.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MktWarehouseDeletionServiceTest {
    MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MktAuditService audit = mock(MktAuditService.class);
    MktWarehouseDeletionService service = new MktWarehouseDeletionService(warehouses, jdbc, audit);
    MktWarehouse w = new MktWarehouse();
    @BeforeEach void setup() {
        w.setId(7L); w.setCode("WH-7"); w.setName("测试云仓"); w.setJoinStatus(5);
        when(warehouses.selectForUpdate(7L)).thenReturn(w);
        when(jdbc.queryForList(anyString(),eq(Long.class),any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
    }
    @Test void requestDeletesImmediatelyAndRevokesAccessWithoutGracePeriod() {
        service.request(7L, " 误录 ");
        var requested = ArgumentCaptor.forClass(LocalDateTime.class);
        var due = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jdbc).update(contains("deleted=1,join_status=7"),requested.capture(),due.capture(),eq("误录"),eq("system"),eq(7L),eq(5));
        assertThat(due.getValue()).isEqualTo(requested.getValue());
        for (String table : List.of("crm_marketing_credential","crm_warehouse_contact","crm_warehouse_erp","crm_competition_protection", "crm_customer_operation_warehouse", "crm_operation_warehouse"))
            verify(jdbc).update(startsWith("UPDATE " + table + " SET"),eq("system"),eq(7L));
        verify(audit).logRequired(eq("warehouse.delete.complete"),eq(w),eq("误录"),any(Map.class),any(Map.class));
        verify(jdbc,never()).update(startsWith("DELETE"),any(Object[].class));
        verify(jdbc,never()).update(contains("UPDATE crm_service_contract"),any(Object[].class));
    }
    @Test void administratorCanImmediatelyDeleteLegacyPendingWarehouse() {
        w.setJoinStatus(8); w.setDeleteDueAt(LocalDateTime.now().plusDays(7));
        service.request(7L,"立即删除");
        verify(jdbc).update(contains("deleted=1,join_status=7"),any(LocalDateTime.class),any(LocalDateTime.class),eq("立即删除"),eq("system"),eq(7L),eq(8));
    }
    @Test void reasonsRequiredAndBoundedBeforeDatabaseAccess() {
        for (String reason : new String[]{null," ","原".repeat(501)})
            assertThatThrownBy(() -> service.request(7L,reason)).hasMessageContaining("500字");
        verifyNoInteractions(warehouses,jdbc,audit);
    }
    @Test void customersWithoutUnfinishedContractsReturnToUnassignedAndAreAudited() {
        when(jdbc.queryForList(contains("FROM crm_customer WHERE"),eq(Long.class),eq(7L))).thenReturn(List.of(2L));
        service.request(7L,"误录");
        verify(jdbc).update(contains("assigned_warehouse_id=NULL,warehouse_assignment_status=0"),eq("system"),eq(2L),eq(7L));
        verify(audit).logRequired(eq("warehouse.delete.complete"),eq(w),eq("误录"),any(),argThat(after -> ((Map<?,?>)after).get("releasedCustomerIds").equals(List.of(2L))));
    }
    @Test void customersWithOtherUnfinishedContractsStillBlockDeletion() {
        when(jdbc.queryForList(contains("FROM crm_customer WHERE"),eq(Long.class),eq(7L))).thenReturn(List.of(2L));
        when(jdbc.queryForList(contains("FROM crm_service_contract WHERE customer_id"),eq(Long.class),eq(2L))).thenReturn(List.of(99L));
        assertThatThrownBy(() -> service.request(7L,"误录")).hasMessageContaining("客户归属");
        verify(jdbc,never()).update(anyString(),any(Object[].class)); verifyNoInteractions(audit);
    }
    @Test void dueLegacyFrozenWarehouseAlsoReleasesIdleCustomers() {
        w.setJoinStatus(8); w.setDeleteDueAt(LocalDateTime.now().minusMinutes(1));
        when(jdbc.queryForList(contains("FROM crm_customer WHERE"),eq(Long.class),eq(7L))).thenReturn(List.of(2L));
        assertThat(service.completePending(7L)).isTrue();
        verify(jdbc).update(contains("assigned_warehouse_id=NULL"),eq("system"),eq(2L),eq(7L));
    }
    @Test void unfinishedContractsBlockRequest() {
        when(jdbc.queryForList(contains("FROM crm_service_contract"),eq(Long.class),eq(7L))).thenReturn(List.of(2L));
        assertThatThrownBy(() -> service.request(7L,"误录")).hasMessageContaining("合同");
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void activeYunshanMappingBlocksDeletion() {
        when(jdbc.queryForList(contains("FROM crm_yunshan_volume_map"), eq(Long.class), eq(7L))).thenReturn(List.of(2L));
        assertThatThrownBy(() -> service.request(7L, "误录")).hasMessageContaining("发货量客户关联");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(audit);
    }
    @Test void repeatedDeletionOfArchivedWarehouseIsRejected() {
        when(warehouses.selectForUpdate(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.request(7L,"重复")).hasMessageContaining("已删除");
        verifyNoInteractions(jdbc,audit);
    }
    @Test void legacyPendingWithoutDeadlineIsStillCompleted() {
        w.setJoinStatus(8);
        assertThat(service.completePending(7L)).isTrue();
        verify(audit).logRequired(eq("warehouse.delete.complete"),eq(w),isNull(),any(),any());
    }
    @Test void jobSkipsActiveAndAlreadyArchivedWarehouses() {
        assertThat(service.completePending(7L)).isFalse();
        when(warehouses.selectForUpdate(7L)).thenReturn(null);
        assertThat(service.completePending(7L)).isFalse();
        verifyNoInteractions(jdbc,audit);
    }
    @Test void legacyPendingDeletionDoesNotWaitForOldDeadlineAndPreservesArchives() {
        w.setJoinStatus(8); w.setDeleteDueAt(LocalDateTime.now().plusDays(7)); w.setDeleteReason("误录");
        assertThat(service.completePending(7L)).isTrue();
        verify(jdbc).update(contains("deleted=1,join_status=7"),any(LocalDateTime.class),any(LocalDateTime.class),eq("误录"),eq("system"),eq(7L),eq(8));
        for (String table : List.of("crm_marketing_credential","crm_warehouse_contact","crm_warehouse_erp","crm_competition_protection"))
            verify(jdbc).update(startsWith("UPDATE " + table + " SET"),eq("system"),eq(7L));
        verify(jdbc,never()).update(startsWith("DELETE"),any(Object[].class));
        verify(jdbc,never()).update(contains("UPDATE crm_service_contract"),any(Object[].class));
        verify(audit).logRequired(eq("warehouse.delete.complete"),eq(w),eq("误录"),any(Map.class),any(Map.class));
    }
    @Test void failedConditionalUpdateDoesNotRecordSuccess() {
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.request(7L,"误录")).hasMessageContaining("状态已变化");
        verifyNoInteractions(audit);
    }
    @Test void auditFailurePropagatesForTransactionalRollback() {
        doThrow(new BizException("维护记录保存失败")).when(audit).logRequired(anyString(),any(),anyString(),any(),any());
        assertThatThrownBy(() -> service.request(7L,"误录")).hasMessageContaining("维护记录保存失败");
    }
}
