package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktCommissionService;
import com.zhyq.park.marketing.service.MktReferralOrderImportService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErpOrderOwnershipTest {
    @Mock MktWarehouseMapper warehouses;
    @Mock MktWarehouseErpMapper credentials;
    @Mock MktCustomerErpMapMapper maps;
    @Mock MktErpSyncLogMapper logs;
    @Mock MktErpEventInboxMapper inbox;
    @Mock MktErpUnmappedMapper unmapped;
    @Mock MktReferralOrderMapper orders;
    @Mock MktReferralOrderImportService importer;
    @Mock MktCommissionService commissions;
    @Mock BizSettings settings;
    @InjectMocks ErpOrderIngestService service;
    @BeforeAll static void metadata() {
        var a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (var c : List.of(MktWarehouse.class, MktWarehouseErp.class, MktErpEventInbox.class, MktReferralOrder.class))
            TableInfoHelper.initTableInfo(a,c);
    }
    @BeforeEach void warehouse() {
        var w = new MktWarehouse(); w.setId(1L); w.setCode("W1"); when(warehouses.selectById(1L)).thenReturn(w);
    }
    MktWarehouseErp credential() { var c = new MktWarehouseErp(); c.setId(1L); c.setWarehouseId(1L); c.setAppId("app1"); return c; }
    com.fasterxml.jackson.databind.JsonNode event(String type) throws Exception {
        return new ObjectMapper().readTree("{\"event\":\"" + type + "\",\"event_id\":\"E1\",\"customer_code\":\"C1\",\"warehouse_code\":\"W1\",\"order_no\":\"SAME-NO\",\"occurred_at\":\"2026-09-21T10:00:00+08:00\"}");
    }
    @Test void refundOnlySearchesCredentialWarehouseAndCannotTouchOtherWarehouse() throws Exception {
        when(orders.selectOne(any())).thenAnswer(i -> {
            var q = (AbstractWrapper<?,?,?>) i.getArgument(0);
            assertThat(q.getSqlSegment()).contains("warehouse_id", "FOR UPDATE");
            assertThat(q.getParamNameValuePairs().values()).contains(1L);
            return null; // the global same-number order belongs to warehouse 2
        });
        var result = service.ingest(credential(), event("order.refunded"), "digest");
        assertThat(result.ok()).isTrue(); assertThat(result.orderId()).isNull();
        verify(orders, never()).update(any(),any()); verifyNoInteractions(commissions);
    }
    @Test void cancellationUpdateAlsoChecksWarehouseAndStatus() throws Exception {
        var own = new MktReferralOrder(); own.setId(20L); own.setWarehouseId(1L);
        when(orders.selectOne(any())).thenReturn(own);
        when(orders.update(isNull(),any())).thenAnswer(i -> {
            var q = (Wrapper<?>) i.getArgument(1); assertThat(q.getSqlSegment()).contains("warehouse_id", "status"); return 1;
        });
        var result = service.ingest(credential(), event("order.cancelled"), "digest");
        assertThat(result.orderId()).isEqualTo(20L); verify(commissions).clawback(20L,"ERP 取消");
    }
    @Test void eventIdCannotBeReusedWithDifferentPayload() throws Exception {
        var old = new MktErpEventInbox(); old.setStatus("PROCESSED"); old.setPayloadDigest("original"); when(inbox.selectOne(any())).thenReturn(old);
        var result = service.ingest(credential(),event("order.refunded"),"changed");
        assertThat(result.ok()).isFalse(); assertThat(result.code()).isEqualTo("EVENT_CONFLICT");
        verifyNoInteractions(orders,commissions);
    }
}
