package com.zhyq.park.receivable;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V32MigrationContractTest {

    @Test
    void migrationContainsRequiredTablesIndexesAndPermissions() throws Exception {
        try (var in = getClass().getResourceAsStream("/db/migration/V32__receivable_import.sql")) {
            assertNotNull(in);
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String token : new String[]{
                    "CREATE TABLE sys_import_batch",
                    "CREATE TABLE sys_import_row",
                    "CREATE TABLE fin_receivable_register",
                    "uk_receivable_business",
                    "CREATE TABLE fin_receivable_rule",
                    "CREATE TABLE fin_deposit_ledger",
                    "CREATE TABLE fin_collection_account",
                    "billing_key",
                    "uk_bill_billing_key",
                    "finance:receivable:query",
                    "finance:receivable:import",
                    "finance:receivable:confirm",
                    "finance:receivable:generate",
                    "finance:receivable:account:view"
            }) {
                assertTrue(sql.contains(token), token);
            }
            assertTrue(sql.contains("billing_active_key"));
            assertTrue(sql.contains("IF(deleted = 0, billing_key, NULL)"));
            assertFalse(sql.contains("(billing_key, deleted)"));
        }
    }
}
