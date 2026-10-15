package com.zhyq.park.marketing.wh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktErpSyncLogMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.open.ErpConnectionService;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhErpControllerTest {
    @Mock MktWarehouseMapper warehouses;
    @Mock MktWarehouseErpMapper erps;
    @Mock MktErpSyncLogMapper logs;
    @Mock FieldEncryptionService encryption;
    @Mock ErpConnectionService connection;
    WhErpController controller;

    @BeforeEach void setup() {
        controller = new WhErpController(warehouses, erps, logs, encryption, connection, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("wh:11", null, List.of()));
        MktWarehouse warehouse = new MktWarehouse(); warehouse.setId(11L);
        lenient().when(warehouses.selectById(11L)).thenReturn(warehouse);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void dryRunValidatesEveryEventAndReportsOnlyValidRowsAsAccepted() {
        Map<String, Object> valid = shipped();
        Map<String, Object> invalid = shipped(); invalid.remove("logistics");
        Map<String, Object> result = controller.testOrders(List.of(valid, invalid)).getData();
        assertThat(result).containsEntry("accepted", 1).containsEntry("rejected", 1)
                .containsEntry("validationOnly", true).containsEntry("businessValidated", false)
                .containsEntry("formalCommission", false);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows.get(0)).containsEntry("ok", true);
        assertThat(rows.get(1)).containsEntry("ok", false);
        assertThat(rows.get(1).get("msg").toString()).contains("tracking_no");
        verifyNoInteractions(erps, logs, encryption, connection);
    }

    @Test void malformedEventIsRejectedInsteadOfBeingCountedAsAValidOrder() {
        assertThat(controller.testOrders(List.of(Map.of("event", "order.shipped"))).getData())
                .containsEntry("accepted", 0).containsEntry("rejected", 1);
        verifyNoInteractions(erps, logs, encryption, connection);
    }

    @Test void emptyOrOversizedSelfTestIsRejected() {
        assertThatThrownBy(() -> controller.testOrders(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> controller.testOrders(List.of())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> controller.testOrders(java.util.Collections.nCopies(501, shipped()))).isInstanceOf(BizException.class);
        verifyNoInteractions(erps, logs, encryption, connection);
    }

    private static Map<String, Object> shipped() {
        return new LinkedHashMap<>(Map.of("event_id", "shipment-1", "event", "order.shipped", "order_no", "OUT-1",
                "warehouse_code", "WH-1", "customer_code", "C-1", "occurred_at", "2026-10-15T08:00:00+08:00",
                "qty", 1, "packages", 1, "logistics", Map.of("tracking_no", "TRACK-1")));
    }
}
