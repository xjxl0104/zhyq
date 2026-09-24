package com.zhyq.park.marketing;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class V62MigrationContractTest {
    @Test void migrationDefinesIdempotentServiceBillsAndLines() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V62__marketing_service_fee_bills.sql"));
        assertThat(sql).contains("crm_service_fee_bill", "crm_service_fee_bill_line", "uk_service_fee_bill_key", "uk_service_fee_line", "DECIMAL(14,2)");
    }
}
