package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.marketing.entity.MktErpEventInbox;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktErpEventInboxMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** P2-1 重放 worker:重放走 ingest 幂等、无凭证跳过、失败按退避回 RETRY、达上限进死信。 */
@ExtendWith(MockitoExtension.class)
class ErpReplayWorkerTest {
    @Mock MktErpEventInboxMapper inbox;
    @Mock MktWarehouseErpMapper erp;
    @Mock ErpOrderIngestService ingest;

    @BeforeAll static void initTableInfo() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktErpEventInbox.class);
        TableInfoHelper.initTableInfo(a, MktWarehouseErp.class);
    }

    private ErpReplayWorker worker() { return new ErpReplayWorker(inbox, erp, ingest, new ObjectMapper()); }

    private MktErpEventInbox row(long id, int attempts) {
        MktErpEventInbox r = new MktErpEventInbox();
        r.setId(id); r.setWarehouseId(11L); r.setAppId("wh_a"); r.setEventId("e-" + id);
        r.setOrderNo("O-" + id); r.setPayloadDigest("d"); r.setPayloadJson("{\"event\":\"order.paid\",\"order_no\":\"O-" + id + "\"}");
        r.setStatus("RECEIVED"); r.setAttempts(attempts); r.setProjectId(7L);
        return r;
    }

    private MktWarehouseErp cred() { MktWarehouseErp c = new MktWarehouseErp(); c.setId(1L); c.setWarehouseId(11L); c.setAppId("wh_a"); return c; }

    @Test void replaysReceivedEventThroughIngest() {
        when(inbox.selectList(any())).thenReturn(List.of(row(5L, 0)));
        when(erp.selectOne(any())).thenReturn(cred());
        when(ingest.ingest(any(), any(), anyString())).thenReturn(new ErpOrderIngestService.IngestResult(true, 31L, true, null, null));

        int n = worker().replayDue();

        assertThat(n).isEqualTo(1);
        verify(ingest).ingest(any(), any(), eq("d"));
    }

    @Test void skipsWhenCredentialMissingKeepsReceived() {
        when(inbox.selectList(any())).thenReturn(List.of(row(5L, 0)));
        when(erp.selectOne(any())).thenReturn(null);

        int n = worker().replayDue();

        assertThat(n).isZero();
        verify(ingest, never()).ingest(any(), any(), anyString());
    }

    @Test void failureAfterMaxAttemptsGoesToDead() {
        when(inbox.selectList(any())).thenReturn(List.of(row(5L, 4)));   // attempts=4,再失败即第 5 次
        when(erp.selectOne(any())).thenReturn(cred());
        when(ingest.ingest(any(), any(), anyString())).thenReturn(new ErpOrderIngestService.IngestResult(false, null, false, "BIZ", "boom"));

        worker().replayDue();

        ArgumentCaptor<Wrapper<MktErpEventInbox>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(inbox, atLeastOnce()).update(isNull(), cap.capture());
        com.zhyq.park.marketing.support.WrapperAssert wa = com.zhyq.park.marketing.support.WrapperAssert.of(
                (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MktErpEventInbox>) cap.getAllValues().get(cap.getAllValues().size() - 1));
        assertThat(wa.setValue("status")).isEqualTo("DEAD");
    }

    @Test void failureBelowMaxGoesToRetryWithBackoff() {
        when(inbox.selectList(any())).thenReturn(List.of(row(5L, 0)));
        when(erp.selectOne(any())).thenReturn(cred());
        when(ingest.ingest(any(), any(), anyString())).thenReturn(new ErpOrderIngestService.IngestResult(false, null, false, "BIZ", "boom"));

        worker().replayDue();

        ArgumentCaptor<Wrapper<MktErpEventInbox>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(inbox, atLeastOnce()).update(isNull(), cap.capture());
        com.zhyq.park.marketing.support.WrapperAssert wa = com.zhyq.park.marketing.support.WrapperAssert.of(
                (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MktErpEventInbox>) cap.getAllValues().get(cap.getAllValues().size() - 1));
        assertThat(wa.setValue("status")).isEqualTo("RETRY");
    }
}
