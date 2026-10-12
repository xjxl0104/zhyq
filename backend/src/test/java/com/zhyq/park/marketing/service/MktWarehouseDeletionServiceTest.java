package com.zhyq.park.marketing.service;

import com.zhyq.park.auth.JwtAccountService;
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
    @Test void requestFreezesImmediatelyAndSchedulesExactlySevenDaysWithoutRemovingCredentials() {
        service.request(7L, " 误录 ");
        var requested = ArgumentCaptor.forClass(LocalDateTime.class);
        var due = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jdbc).update(contains("delete_previous_status=join_status,join_status=8"),requested.capture(),due.capture(),eq("误录"),eq("system"),eq(7L));
        assertThat(due.getValue()).isEqualTo(requested.getValue().plusDays(7));
        verify(jdbc,never()).update(contains("crm_marketing_credential"),any(Object[].class));
        verify(audit).logRequired(eq("warehouse.delete.request"),eq(w),eq("误录"),any(Map.class),any(Map.class));
        w.setJoinStatus(8);
        assertThatThrownBy(() -> JwtAccountService.assertWarehouseActive(w)).hasMessageContaining("冻结待删除");
    }
    @Test void repeatedRequestDoesNotResetDeadline() {
        w.setJoinStatus(8);
        assertThatThrownBy(() -> service.request(7L,"重复")).hasMessageContaining("重复");
        verifyNoInteractions(jdbc,audit);
    }
    @Test void reasonsRequiredAndBoundedBeforeDatabaseAccess() {
        for (String reason : new String[]{null," ","原".repeat(501)})
            assertThatThrownBy(() -> service.request(7L,reason)).hasMessageContaining("500字");
        verifyNoInteractions(warehouses,jdbc,audit);
    }
    @Test void servingCustomersBlockRequestBeforeFreeze() {
        when(jdbc.queryForList(contains("FROM crm_customer WHERE"),eq(Long.class),eq(7L))).thenReturn(List.of(2L));
        assertThatThrownBy(() -> service.request(7L,"误录")).hasMessageContaining("客户归属");
        verify(jdbc,never()).update(anyString(),any(Object[].class)); verifyNoInteractions(audit);
    }
    @Test void unfinishedContractsBlockRequest() {
        when(jdbc.queryForList(contains("FROM crm_service_contract"),eq(Long.class),eq(7L))).thenReturn(List.of(2L));
        assertThatThrownBy(() -> service.request(7L,"误录")).hasMessageContaining("合同");
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void cancellationRestoresPreviousStateAndRetainsAudit() {
        w.setJoinStatus(8); w.setDeletePreviousStatus(2); w.setDeleteDueAt(LocalDateTime.now().plusDays(2));
        service.cancel(7L,"保留");
        verify(jdbc).update(contains("join_status=delete_previous_status"),eq("system"),eq(7L));
        verify(audit).logRequired(eq("warehouse.delete.cancel"),eq(w),eq("保留"),any(Map.class),any(Map.class));
    }
    @Test void cancellationAfterDeadlineIsRejectedEvenBeforeScheduledRun() {
        w.setJoinStatus(8); w.setDeleteDueAt(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.cancel(7L,"保留")).hasMessageContaining("7天冻结期");
        verifyNoInteractions(jdbc,audit);
    }
    @Test void earlyOrRepeatedJobCannotDelete() {
        assertThat(service.completeDue(7L)).isFalse();
        w.setJoinStatus(8); w.setDeleteDueAt(LocalDateTime.now().plusMinutes(1));
        assertThat(service.completeDue(7L)).isFalse();
        when(warehouses.selectForUpdate(7L)).thenReturn(null);
        assertThat(service.completeDue(7L)).isFalse();
        verifyNoInteractions(jdbc,audit);
    }
    @Test void dueDeletionRevokesAllAccessRemovesProtectionAndPreservesFinancialArchives() {
        w.setJoinStatus(8); w.setDeleteDueAt(LocalDateTime.now().minusSeconds(1)); w.setDeleteReason("误录");
        assertThat(service.completeDue(7L)).isTrue();
        verify(jdbc).update(contains("SET deleted=1,join_status=7"),eq("system"),eq(7L));
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
