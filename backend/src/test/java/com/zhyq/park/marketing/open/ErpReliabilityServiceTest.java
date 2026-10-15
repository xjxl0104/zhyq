package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.mapper.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.marketing.entity.MktErpEventInbox;
import com.zhyq.park.marketing.entity.MktErpReconcileSnapshot;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktErpDeadLetterMapper;
import com.zhyq.park.marketing.mapper.MktErpEventInboxMapper;
import com.zhyq.park.marketing.mapper.MktErpReconcileSnapshotMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.support.WrapperAssert;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErpReliabilityServiceTest {
    @Mock MktWarehouseErpMapper erp;
    @Mock MktWarehouseMapper warehouses;
    @Mock MktErpEventInboxMapper inbox;
    @Mock MktErpDeadLetterMapper dead;
    @Mock MktErpReconcileSnapshotMapper reconcile;
    @Mock ErpConnectionService connection;

    @BeforeAll static void initTableInfo() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktWarehouse.class);
        TableInfoHelper.initTableInfo(a, MktWarehouseErp.class);
        TableInfoHelper.initTableInfo(a, MktErpEventInbox.class);
        TableInfoHelper.initTableInfo(a, MktErpReconcileSnapshot.class);
    }

    @Test void legacyAmountEstimateCanNeverAuthorizeSettlement() {
        ErpReliabilityService service = new ErpReliabilityService(erp, warehouses, inbox, dead, reconcile, connection);
        assertThatThrownBy(() -> service.snapshot(11L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("100"), new BigDecimal("100"), 7L)).hasMessageContaining("已停用");
        verifyNoInteractions(reconcile);
    }

    @Test void heartbeatJobRefreshesConnectionFromCurrentCredential() {
        MktWarehouseErp e = new MktWarehouseErp(); e.setId(1L); e.setWarehouseId(11L);
        LocalDateTime now = LocalDateTime.now();
        when(erp.selectList(any())).thenReturn(List.of(e)); when(connection.refresh(e, now)).thenReturn(1);
        ErpReliabilityService service = new ErpReliabilityService(erp, warehouses, inbox, dead, reconcile, connection);
        assertThat(service.heartbeat(now)).isEqualTo(1);
        verify(connection).refresh(e, now);
        verifyNoInteractions(warehouses);
    }

    @Test void dueRetryExplicitlyClearsScheduleSoReplayWorkerCanConsumeIt() {
        LocalDateTime now = LocalDateTime.now();
        MktErpEventInbox row = retry(2);
        when(inbox.selectList(any())).thenReturn(List.of(row));
        when(inbox.update(any(), any())).thenReturn(1);
        assertThat(service().retryDue(now)).isEqualTo(1);
        ArgumentCaptor<LambdaUpdateWrapper<MktErpEventInbox>> capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(inbox).update(isNull(), capture.capture());
        WrapperAssert update = WrapperAssert.of(capture.getValue());
        assertThat(update.whereEq("status")).isEqualTo("RETRY");
        assertThat(update.setValue("status")).isEqualTo("RECEIVED");
        assertThat(update.setValue("next_retry_at")).isEqualTo("null");
        assertThat(update.where()).contains("next_retry_at <=");
        verify(inbox, never()).updateById(any(MktErpEventInbox.class));
    }

    @Test void retryDueDoesNotCreateDeadLetterIfAConcurrentSuccessAlreadyChangedStatus() {
        when(inbox.selectList(any())).thenReturn(List.of(retry(5)));
        when(inbox.update(any(), any())).thenReturn(0);
        assertThat(service().retryDue(LocalDateTime.now())).isZero();
        verifyNoInteractions(dead);
        ArgumentCaptor<LambdaUpdateWrapper<MktErpEventInbox>> capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(inbox).update(isNull(), capture.capture());
        assertThat(WrapperAssert.of(capture.getValue()).whereEq("status")).isEqualTo("RETRY");
    }

    @Test void manualReplayClearsScheduleAndCannotRegressAConcurrentSuccess() {
        when(inbox.selectById(9L)).thenReturn(retry(2));
        when(inbox.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service().replay(9L, "operator")).hasMessageContaining("状态已变化");
        ArgumentCaptor<LambdaUpdateWrapper<MktErpEventInbox>> capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(inbox).update(isNull(), capture.capture());
        WrapperAssert update = WrapperAssert.of(capture.getValue());
        assertThat(update.whereEq("status")).isEqualTo("RETRY");
        assertThat(update.setValue("next_retry_at")).isEqualTo("null");
        assertThat(update.setValue("processed_at")).isEqualTo("null");
    }

    private ErpReliabilityService service() { return new ErpReliabilityService(erp, warehouses, inbox, dead, reconcile, connection); }
    private MktErpEventInbox retry(int attempts) {
        MktErpEventInbox row = new MktErpEventInbox(); row.setId(9L); row.setWarehouseId(11L);
        row.setStatus("RETRY"); row.setAttempts(attempts); row.setNextRetryAt(LocalDateTime.now().minusMinutes(1)); return row;
    }
}
