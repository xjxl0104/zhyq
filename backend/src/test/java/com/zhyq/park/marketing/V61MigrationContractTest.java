package com.zhyq.park.marketing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V61MigrationContractTest {
    @Test void migrationDefinesReliabilityQueuesAndIdempotencyKeys() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V61__marketing_erp_reliability.sql"));
        assertThat(sql).contains("crm_erp_event_inbox", "crm_erp_dead_letter", "crm_erp_unmapped");
        assertThat(sql).contains("uk_erp_inbox_event", "uk_erp_dead_inbox", "uk_erp_unmapped_event");
        assertThat(sql).contains("next_retry_at", "attempts", "status");
    }
}
