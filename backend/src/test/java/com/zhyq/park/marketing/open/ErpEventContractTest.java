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
}
