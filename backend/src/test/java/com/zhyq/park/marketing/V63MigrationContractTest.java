package com.zhyq.park.marketing;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class V63MigrationContractTest {
    @Test void migrationDefinesSettlementAndDirectPaymentIdempotency() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V63__marketing_warehouse_settlement.sql"));
        assertThat(sql).contains("crm_warehouse_settlement", "crm_warehouse_settlement_line", "crm_direct_sign_payment", "uk_warehouse_settle_period", "uk_direct_sign_payment_no", "DECIMAL(14,2)");
    }
}
