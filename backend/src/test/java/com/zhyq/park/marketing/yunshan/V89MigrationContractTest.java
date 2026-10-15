package com.zhyq.park.marketing.yunshan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class V89MigrationContractTest {
    @Test void storesStringEntitiesAnd64BitVolumeWithIdempotencyAndRevisionHistory() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V89__marketing_yunshan_daily_volume.sql"));
        assertThat(sql).contains("CREATE TABLE crm_yunshan_volume_map", "CREATE TABLE crm_yunshan_daily_volume",
                "CREATE TABLE crm_yunshan_sync_log", "uk_yunshan_map_entity (parent_owner_no, parent_warehouse_no)",
                "uk_yunshan_volume_day (map_id, stat_date)", "first_consign_num", "revised_count", "last_revised_at",
                "days_missing", "error_code", "error_msg", "latest_stat_date", "data_as_of");
        assertThat(sql).containsPattern("parent_owner_no\\s+VARCHAR\\(32\\)")
                .containsPattern("parent_warehouse_no\\s+VARCHAR\\(32\\)")
                .containsPattern("consign_num\\s+BIGINT\\s+NOT NULL")
                .containsPattern("first_consign_num\\s+BIGINT\\s+NOT NULL")
                .containsPattern("total_consign\\s+BIGINT");
        assertThat(sql.split("CHARACTER SET ascii COLLATE ascii_bin", -1)).hasSize(5);
        String statements = sql.replaceAll("(?m)--[^\\r\\n]*", "").toUpperCase(Locale.ROOT);
        assertThat(statements).doesNotContain("ALTER TABLE", "DROP TABLE", "TRUNCATE", "INSERT INTO", "DELETE FROM", "UPDATE CRM_");
        assertThat(statements.split("CREATE TABLE", -1)).hasSize(4);
        assertThat(statements.split("TENANT_ID", -1)).hasSize(4);
    }
}
