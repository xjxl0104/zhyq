package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ErpExceptionServiceTest {
    final MktWarehouseMapper warehouses = mock(MktWarehouseMapper.class);
    final MktWarehouseErpMapper credentials = mock(MktWarehouseErpMapper.class);
    final MktErpEventInboxMapper inbox = mock(MktErpEventInboxMapper.class);
    final MktErpUnmappedMapper unmapped = mock(MktErpUnmappedMapper.class);
    final MktErpDeadLetterMapper dead = mock(MktErpDeadLetterMapper.class);
    final MktAuditService audit = mock(MktAuditService.class);
    final ErpExceptionService service = new ErpExceptionService(warehouses, credentials, inbox, unmapped, dead, audit);

    @BeforeAll static void metadata() {
        var a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(MktErpEventInbox.class, MktErpUnmapped.class, MktErpDeadLetter.class, MktWarehouseErp.class)) TableInfoHelper.initTableInfo(a, c);
    }

    MktWarehouseErp setup(int env) {
        MktWarehouse w = new MktWarehouse(); w.setId(9L); w.setJoinStatus(5);
        when(warehouses.selectForUpdate(9L)).thenReturn(w);
        MktWarehouseErp c = new MktWarehouseErp(); c.setWarehouseId(9L); c.setAppId("wh-a"); c.setStatus(1); c.setEnv(env);
        when(credentials.selectOne(any())).thenReturn(c);
        return c;
    }
    MktErpEventInbox event(String status) {
        var row = new MktErpEventInbox(); row.setId(7L); row.setWarehouseId(9L); row.setAppId("wh-a");
        row.setEvent("order.shipped"); row.setEventId("original-event"); row.setPayloadDigest("original-digest");
        row.setPayloadJson("{}"); row.setStatus(status);
        when(inbox.selectById(7L)).thenReturn(row);
        return row;
    }

    @Test void legacyUnmappedIsQueuedWithOriginalIdentityAndProcessedEventCannotBeRetried() {
        setup(2); var row = event("PROCESSED");
        assertThatThrownBy(() -> service.retry(9L, 7L, "配置已修复")).hasMessageContaining("已完成");
        verify(inbox, never()).update(any(), any());
        row.setLastError("UNMAPPED");
        when(inbox.update(isNull(), any())).thenReturn(1);
        service.retry(9L, 7L, "映射已补齐");
        ArgumentCaptor<LambdaUpdateWrapper<MktErpEventInbox>> capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(inbox).update(isNull(), capture.capture());
        var update = capture.getValue(); update.getSqlSegment();
        assertThat(update.getSqlSet()).doesNotContain("event_id", "payload_digest", "payload_json", "app_id");
        assertThat(update.getParamNameValuePairs().values()).contains(7L, 9L, "PROCESSED", "RECEIVED");
        verify(dead).update(isNull(), any());
    }

    @Test void otherWarehouseAndSandboxCannotReplayIntoFormalLedger() {
        setup(2); event("DEAD").setWarehouseId(10L);
        assertThatThrownBy(() -> service.retry(9L, 7L, "重新处理")).hasMessageContaining("不属于");
        setup(1);
        assertThatThrownBy(() -> service.retry(9L, 7L, "重新处理")).hasMessageContaining("正式");
        verify(inbox, never()).update(any(), any());
    }

    @Test void savingMappingQueuesOnlyUnresolvedShipmentsForCurrentCredential() {
        setup(2); var row = event("WAITING_MAPPING");
        var pending = new MktErpUnmapped(); pending.setInboxId(7L);
        when(unmapped.selectList(any())).thenReturn(List.of(pending));
        when(inbox.update(isNull(), any())).thenReturn(1);
        assertThat(service.enqueueForMapping(9L, "OWNER-A")).isEqualTo(1);
        row.setEvent("order.refunded");
        assertThat(service.enqueueForMapping(9L, "OWNER-A")).isZero();
        row.setEvent("order.shipped"); row.setAppId("old-app");
        assertThat(service.enqueueForMapping(9L, "OWNER-A")).isZero();
        verify(inbox, times(1)).update(any(), any());
    }

    @Test void mappingCanBeSavedInSandboxWithoutReplayingHistoricalMoney() {
        setup(1);
        assertThat(service.enqueueForMapping(9L, "OWNER-A")).isZero();
        verifyNoInteractions(inbox, unmapped);
    }

    @Test void reasonIsRequiredAndProcessingIsNotRequeued() {
        assertThatThrownBy(() -> service.retry(9L, 7L, " ")).hasMessageContaining("原因");
        verifyNoInteractions(warehouses);
        setup(2); event("PROCESSING");
        assertThatThrownBy(() -> service.retry(9L, 7L, "重试")).hasMessageContaining("正在处理");
        verify(inbox, never()).update(any(), any());
    }
}
