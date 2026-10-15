package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.*;
import com.zhyq.park.marketing.support.WrapperAssert;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErpOrderIngestServiceTest {
    @Mock MktWarehouseMapper warehouses;
    @Mock MktWarehouseErpMapper credentials;
    @Mock MktCustomerErpMapMapper mappings;
    @Mock MktErpSyncLogMapper logs;
    @Mock MktErpEventInboxMapper inbox;
    @Mock MktErpUnmappedMapper unmapped;
    @Mock MktReferralOrderMapper orders;
    @Mock MktReferralOrderImportService imports;
    @Mock MktCommissionService commissions;
    @Mock MktOutboundOrderStateService states;
    private final ObjectMapper json = new ObjectMapper();
    private ErpOrderIngestService service;
    private MktWarehouseErp credential;

    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : new Class<?>[]{MktWarehouse.class, MktWarehouseErp.class, MktCustomerErpMap.class,
                MktErpSyncLog.class, MktErpEventInbox.class, MktErpUnmapped.class, MktReferralOrder.class})
            TableInfoHelper.initTableInfo(assistant, type);
    }

    @BeforeEach void setup() {
        service = new ErpOrderIngestService(warehouses, credentials, mappings, logs, inbox, unmapped, orders, imports, commissions, states);
        credential = new MktWarehouseErp(); credential.setId(3L); credential.setWarehouseId(7L);
        credential.setAppId("wh_a"); credential.setEnv(2); credential.setStatus(1); credential.setSecretEnc("encrypted");
        credential.setConnectionRevision(0); credential.setProjectId(1L);
        MktWarehouse warehouse = new MktWarehouse(); warehouse.setId(7L); warehouse.setCode("W7");
        warehouse.setJoinStatus(5); warehouse.setProjectId(1L);
        lenient().when(warehouses.selectForUpdate(7L)).thenReturn(warehouse);
        lenient().when(credentials.selectById(3L)).thenReturn(credential);
        lenient().doAnswer(call -> { MktErpEventInbox row = call.getArgument(0); row.setId(11L); return 1; })
                .when(inbox).insert(any(MktErpEventInbox.class));
    }

    private ObjectNode event(String type, String id) {
        ObjectNode node = json.createObjectNode(); node.put("event", type); node.put("event_id", id);
        node.put("order_no", "O1"); node.put("warehouse_code", "W7"); node.put("customer_code", "C1");
        node.put("occurred_at", LocalDateTime.now().minusDays(1).withNano(0).toString());
        node.put("qty", 2); node.put("packages", 1); node.putObject("logistics").put("tracking_no", "SF1");
        return node;
    }

    private MktErpEventInbox pending(String status) {
        MktErpEventInbox row = new MktErpEventInbox(); row.setId(11L); row.setStatus(status);
        row.setEvent("order.shipped"); row.setLastError("UNMAPPED"); row.setPayloadDigest("digest");
        return row;
    }

    @Test void sandboxValidatesRealContractWithoutWritingProductionData() {
        credential.setEnv(1);
        var result = service.ingest(credential, event("order.shipped", "E1"), "digest");
        assertThat(result.ok()).isTrue(); assertThat(result.sandbox()).isTrue();
        assertThat(result.status()).isEqualTo("SANDBOX_VALIDATED");
        verifyNoInteractions(inbox, unmapped, orders, imports, commissions, logs, states, mappings);
        verify(credentials, never()).update(any(), any());
    }

    @Test void sandboxCannotHideInvalidFractionalQuantity() {
        credential.setEnv(1); ObjectNode payload = event("order.shipped", "E1"); payload.put("qty", 1.5);
        var result = service.ingest(credential, payload, "digest");
        assertThat(result.ok()).isFalse(); assertThat(result.code()).isEqualTo("BAD_PAYLOAD");
        verifyNoInteractions(inbox, unmapped, orders, imports, commissions, logs);
    }

    @Test void staleVerifiedCredentialCannotWriteAfterEnvironmentChange() {
        MktWarehouseErp changed = new MktWarehouseErp(); changed.setId(3L); changed.setWarehouseId(7L);
        changed.setStatus(1); changed.setAppId("wh_a"); changed.setSecretEnc("encrypted"); changed.setEnv(1);
        when(credentials.selectById(3L)).thenReturn(changed);
        assertThat(service.ingest(credential, event("order.shipped", "E1"), "digest").code()).isEqualTo("CREDENTIAL_CHANGED");
        verifyNoInteractions(inbox, unmapped, orders, imports, commissions, states);
    }

    @Test void unmappedShipmentIsPendingAndDoesNotCountAsSuccessfulSynchronization() {
        var result = service.ingest(credential, event("order.shipped", "E1"), "digest");
        assertThat(result.ok()).isTrue(); assertThat(result.status()).isEqualTo("WAITING_MAPPING");
        ArgumentCaptor<LambdaUpdateWrapper<MktErpEventInbox>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(inbox).update(isNull(), update.capture());
        assertThat(WrapperAssert.of(update.getValue()).setValue("status")).isEqualTo("WAITING_MAPPING");
        verify(unmapped).insert(any(MktErpUnmapped.class));
        verifyNoInteractions(imports, commissions);
        verify(credentials, never()).update(any(), any());
    }

    @Test void repeatedWaitingEventDoesNotCreateAnotherExceptionRow() {
        when(inbox.selectOne(any())).thenReturn(pending("WAITING_MAPPING"));
        when(unmapped.selectOne(any())).thenReturn(new MktErpUnmapped());
        assertThat(service.ingest(credential, event("order.shipped", "E1"), "digest").status()).isEqualTo("WAITING_MAPPING");
        verify(inbox, never()).insert(any(MktErpEventInbox.class));
        verify(unmapped, never()).insert(any(MktErpUnmapped.class));
    }

    @Test void changedPayloadIsRejectedEvenForPendingEvents() {
        when(inbox.selectOne(any())).thenReturn(pending("WAITING_MAPPING"));
        assertThat(service.ingest(credential, event("order.shipped", "E1"), "different").code()).isEqualTo("EVENT_CONFLICT");
        verifyNoInteractions(imports, commissions, unmapped, states);
    }

    @Test void batchItemCanBeResentAsSingleJsonWithoutChangingOriginalIdentity() {
        ObjectNode event = event("order.shipped", "E1");
        MktErpEventInbox original = pending("WAITING_MAPPING");
        original.setPayloadJson(event.toPrettyString()); original.setPayloadDigest("original-body-digest");
        when(inbox.selectOne(any())).thenReturn(original);
        when(unmapped.selectOne(any())).thenReturn(new MktErpUnmapped());
        assertThat(service.ingest(credential, event, "batch-item-digest").status()).isEqualTo("WAITING_MAPPING");
        assertThat(original.getPayloadDigest()).isEqualTo("original-body-digest");
        assertThat(original.getPayloadJson()).isEqualTo(event.toPrettyString());
    }

    @Test void realChangeInsideOriginalJsonRemainsAnEventConflict() {
        ObjectNode event = event("order.shipped", "E1");
        MktErpEventInbox original = pending("WAITING_MAPPING"); original.setPayloadJson(event.toString());
        when(inbox.selectOne(any())).thenReturn(original);
        event.put("qty", 8);
        assertThat(service.ingest(credential, event, "changed-content").code()).isEqualTo("EVENT_CONFLICT");
        verifyNoInteractions(imports, commissions, unmapped, states);
    }

    @Test void legacyProcessedUnmappedEventCanBeReprocessedAfterMapping() {
        when(inbox.selectOne(any())).thenReturn(pending("PROCESSED"));
        MktCustomerErpMap map = new MktCustomerErpMap(); map.setCustomerId(9L);
        when(mappings.selectOne(any())).thenReturn(map);
        MktReferralOrder order = new MktReferralOrder(); order.setId(19L); order.setCustomerId(9L);
        when(imports.ingestOutbound(any(), eq(1L), eq(9L)))
                .thenReturn(new MktReferralOrderImportService.OutboundResult(order, true, false));
        when(orders.selectById(19L)).thenReturn(order);
        var result = service.ingest(credential, event("order.shipped", "E1"), "digest");
        assertThat(result.orderId()).isEqualTo(19L); assertThat(result.attributed()).isTrue();
        verify(imports).ingestOutbound(any(), eq(1L), eq(9L));
        verifyNoInteractions(commissions); // shared importer selects contract type and performs financial entry
        ArgumentCaptor<LambdaUpdateWrapper<MktErpEventInbox>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(inbox).update(isNull(), update.capture());
        assertThat(WrapperAssert.of(update.getValue()).setValue("status")).isEqualTo("PROCESSED");
        assertThat(WrapperAssert.of(update.getValue()).setColumns()).contains("last_error");
        assertThat(WrapperAssert.of(update.getValue()).setValue("last_error")).isEqualTo("null");
        verify(unmapped).update(isNull(), any());
        verify(credentials).update(isNull(), any());
    }

    @Test void createdAndPaidEventsWithoutLedgerAreNotMissingCustomerMappings() {
        assertThat(service.ingest(credential, event("order.created", "E1"), "d1").status()).isEqualTo("PROCESSED");
        assertThat(service.ingest(credential, event("order.paid", "E2"), "d2").status()).isEqualTo("PROCESSED");
        verify(unmapped, never()).insert(any(MktErpUnmapped.class));
        verifyNoInteractions(mappings, imports, commissions);
    }

    @Test void cancellationBeforeShipmentLeavesTerminalIdentityAndBlocksLateShipment() {
        var cancelled = event("order.cancelled", "CANCEL-1");
        assertThat(service.ingest(credential, cancelled, "cancel").ok()).isTrue();
        verify(states).terminate(eq(7L), eq("O1"), eq("order.cancelled"), eq("wh_a"), eq("CANCEL-1"), any(), eq(1L));
        when(states.isTerminal(7L, "O1")).thenReturn(true);
        var result = service.ingest(credential, event("order.shipped", "SHIP-1"), "ship");
        assertThat(result.status()).isEqualTo("IGNORED_TERMINAL");
        verifyNoInteractions(imports, commissions, mappings);
        verify(unmapped, never()).insert(any(MktErpUnmapped.class));
    }

    @Test void duplicateRefundCannotClawBackTwice() {
        MktReferralOrder order = new MktReferralOrder(); order.setId(19L);
        when(orders.selectOne(any())).thenReturn(order);
        when(orders.update(isNull(), any())).thenReturn(1, 0);
        assertThat(service.ingest(credential, event("order.refunded", "R1"), "r1").ok()).isTrue();
        assertThat(service.ingest(credential, event("order.refunded", "R2"), "r2").ok()).isTrue();
        verify(commissions, times(1)).clawback(19L, "ERP 退款");
    }

    @Test void anotherEventIdForAnExistingOrderCannotOverwriteOriginalEvidence() {
        MktCustomerErpMap map = new MktCustomerErpMap(); map.setCustomerId(9L);
        when(mappings.selectOne(any())).thenReturn(map);
        MktReferralOrder order = new MktReferralOrder(); order.setId(19L); order.setCustomerCode("ORIGINAL-CODE"); order.setRawPayload("original-payload");
        when(imports.ingestOutbound(any(), eq(1L), eq(9L)))
                .thenReturn(new MktReferralOrderImportService.OutboundResult(order, false, false));
        var result = service.ingest(credential, event("order.shipped", "ANOTHER-EVENT"), "another-digest");
        assertThat(result.ok()).isTrue(); assertThat(result.orderId()).isEqualTo(19L);
        verify(orders, never()).update(any(), any());
        assertThat(order.getCustomerCode()).isEqualTo("ORIGINAL-CODE"); assertThat(order.getRawPayload()).isEqualTo("original-payload");
    }

    @Test void conflictReturnsExplicitCodeAndDefersItsFailureLogUntilAfterRollback() {
        MktCustomerErpMap map = new MktCustomerErpMap(); map.setCustomerId(9L);
        when(mappings.selectOne(any())).thenReturn(map);
        when(imports.ingestOutbound(any(), eq(1L), eq(9L)))
                .thenThrow(new com.zhyq.park.common.exception.BizException("ORDER_CONFLICT: 件数与原单不符"));
        ObjectNode payload = event("order.shipped", "ANOTHER-EVENT");
        var result = service.ingest(credential, payload, "another-digest");
        assertThat(result.ok()).isFalse(); assertThat(result.code()).isEqualTo("ORDER_CONFLICT");
        assertThat(result.auditAfterRollback()).isTrue();
        verifyNoInteractions(logs);
        service.logRolledBackFailure(credential, payload, "another-digest", result);
        ArgumentCaptor<MktErpSyncLog> logged = ArgumentCaptor.forClass(MktErpSyncLog.class);
        verify(logs).insert(logged.capture());
        assertThat(logged.getValue().getWarehouseId()).isEqualTo(7L);
        assertThat(logged.getValue().getOrderNo()).isEqualTo("O1"); assertThat(logged.getValue().getErrorCode()).isEqualTo("ORDER_CONFLICT");
        verify(orders, never()).update(any(), any());
    }

    @Test void alreadyRecordedAndSandboxFailuresAreNotLoggedAgain() {
        service.logRolledBackFailure(credential, event("order.paid", "E1"), "digest",
                new ErpOrderIngestService.IngestResult(false, null, false, "EVENT_CONFLICT", "冲突"));
        credential.setEnv(1);
        service.logRolledBackFailure(credential, event("order.paid", "E1"), "digest",
                new ErpOrderIngestService.IngestResult(false, null, false, "BAD_PAYLOAD", "错误", true));
        verifyNoInteractions(logs);
    }
}
