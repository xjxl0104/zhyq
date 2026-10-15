package com.zhyq.park.marketing.open;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErpEventContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void requiresContractFieldsAndPositiveShippedQuantities() throws Exception {
        String json = "{\"event\":\"order.shipped\",\"order_no\":\"O-1\",\"occurred_at\":\"2026-09-21T10:00:00+08:00\",\"warehouse_code\":\"WH-1\",\"customer_code\":\"C-1\",\"qty\":2,\"packages\":1,\"logistics\":{\"tracking_no\":\"T-1\"}}";
        ErpEventContract.Event e = ErpEventContract.parseAndValidate(mapper.readTree(json));
        assertThat(e.eventId()).isEqualTo("order.shipped:O-1:2026-09-21T10:00:00+08:00");
        assertThat(e.qty()).isEqualTo(2);
    }

    @Test void rejectsMissingWarehouseAndBadQuantity() throws Exception {
        String json = "{\"event\":\"order.shipped\",\"order_no\":\"O-1\",\"occurred_at\":\"2026-09-21T10:00:00+08:00\",\"customer_code\":\"C-1\",\"qty\":0,\"packages\":1,\"logistics\":{\"tracking_no\":\"T-1\"}}";
        assertThatThrownBy(() -> ErpEventContract.parseAndValidate(mapper.readTree(json)))
                .isInstanceOf(BizException.class).hasMessageContaining("必填");
    }

    @Test void quantitiesCannotBeSilentlyTruncatedAndAmountsCannotBeCoerced() throws Exception {
        var event = mapper.createObjectNode().put("event", "order.shipped").put("order_no", "O1")
                .put("occurred_at", "2026-09-21T10:00:00+08:00").put("warehouse_code", "W1")
                .put("customer_code", "C1").put("qty", 1.5).put("packages", 1);
        event.putObject("logistics").put("tracking_no", "SF1");
        assertThatThrownBy(() -> ErpEventContract.parseAndValidate(event)).isInstanceOf(BizException.class).hasMessageContaining("整数");
        event.put("qty", 2); event.put("goods_amount", "10.00");
        assertThatThrownBy(() -> ErpEventContract.parseAndValidate(event)).isInstanceOf(BizException.class).hasMessageContaining("goods_amount");
    }

    @Test void offsetTimestampsAreNormalizedToChinaBusinessDate() throws Exception {
        var event = mapper.createObjectNode().put("event", "order.created").put("order_no", "O1")
                .put("occurred_at", "2026-09-20T18:30:00Z").put("warehouse_code", "W1").put("customer_code", "C1");
        assertThat(ErpEventContract.parseAndValidate(event).occurredAt())
                .isEqualTo(java.time.LocalDateTime.of(2026, 9, 21, 2, 30));
    }
}
