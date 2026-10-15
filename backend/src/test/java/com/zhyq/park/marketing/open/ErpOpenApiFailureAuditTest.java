package com.zhyq.park.marketing.open;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The controller calls the failure audit only after the transactional ingest proxy has returned. */
@ExtendWith(MockitoExtension.class)
class ErpOpenApiFailureAuditTest {
    @Mock ErpSignatureService signatures;
    @Mock ErpOrderIngestService ingest;
    private final ObjectMapper json = new ObjectMapper();

    @Test void singleFailureAuditRunsAfterTheLedgerCallAndCarriesWarehouseIdentity() {
        MktWarehouseErp credential = credential();
        String body = "{\"event\":\"order.shipped\",\"order_no\":\"O1\"}";
        String digest = ErpSignatureService.sha256Hex(body);
        var failure = new ErpOrderIngestService.IngestResult(false, null, false, "ORDER_CONFLICT", "订单内容冲突", true);
        when(signatures.verify(any(), any(), any(), any(), eq(body))).thenReturn(new ErpSignatureService.Ok(credential));
        when(ingest.ingest(eq(credential), any(), eq(digest))).thenReturn(failure);
        var response = new ErpOpenApiController(signatures, ingest, json).orderEvent("app", "ts", "nonce", "sig", body);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("code", "ORDER_CONFLICT");
        var sequence = inOrder(ingest);
        sequence.verify(ingest).ingest(eq(credential), any(), eq(digest));
        sequence.verify(ingest).logRolledBackFailure(eq(credential), any(), eq(digest), same(failure));
    }

    @Test void existingRejectionLogIsNotDuplicated() {
        String body = "{\"order_no\":\"O1\"}";
        when(signatures.verify(any(), any(), any(), any(), eq(body))).thenReturn(new ErpSignatureService.Ok(credential()));
        when(ingest.ingest(any(), any(), any())).thenReturn(new ErpOrderIngestService.IngestResult(false, null, false, "EVENT_CONFLICT", "冲突"));
        new ErpOpenApiController(signatures, ingest, json).orderEvent("app", "ts", "nonce", "sig", body);
        verify(ingest, never()).logRolledBackFailure(any(), any(), any(), any());
    }

    @Test void batchRecordsOnlyTheRolledBackItemAndContinuesWithFollowingItem() {
        String body = "[{\"order_no\":\"BAD\"},{\"order_no\":\"GOOD\"}]";
        MktWarehouseErp credential = credential();
        var failure = new ErpOrderIngestService.IngestResult(false, null, false, "BIZ", "缺少定价", true);
        when(signatures.verify(any(), any(), any(), any(), eq(body))).thenReturn(new ErpSignatureService.Ok(credential));
        when(ingest.ingest(any(), any(), any())).thenReturn(failure, new ErpOrderIngestService.IngestResult(true, 5L, true, null, null));
        var response = new ErpOpenApiController(signatures, ingest, json).batch("app", "ts", "nonce", "sig", body);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(ingest, times(2)).ingest(eq(credential), any(), anyString());
        verify(ingest, times(1)).logRolledBackFailure(eq(credential), argThat(node -> "BAD".equals(node.path("order_no").asText())), anyString(), same(failure));
    }

    private static MktWarehouseErp credential() {
        MktWarehouseErp credential = new MktWarehouseErp(); credential.setId(1L); credential.setWarehouseId(7L); credential.setEnv(2); return credential;
    }
}
