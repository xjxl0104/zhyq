package com.zhyq.park.vending;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V33MigrationContractTest {

    @Test
    void migrationContainsFiveTablesAppAndPermissions() throws Exception {
        try (var in = getClass().getResourceAsStream("/db/migration/V33__vending_integration.sql")) {
            assertNotNull(in);
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String token : List.of(
                    "CREATE TABLE ops_vending_machine",
                    "CREATE TABLE ops_vending_sale",
                    "CREATE TABLE ops_vending_restock",
                    "CREATE TABLE ops_vending_fault",
                    "CREATE TABLE ops_vending_reconciliation",
                    "自动售货机",
                    "/app/vending",
                    "vending:query",
                    "vending:import",
                    "vending:open",
                    "vending:config")) {
                assertTrue(sql.contains(token), token);
            }
        }
    }
}
