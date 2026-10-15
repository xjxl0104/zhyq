package com.zhyq.park.marketing.open;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Recover only the known, partially committed MySQL V90 collation failure. */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class ErpV90MigrationRecovery {
    private static final String SCRIPT = "V90__erp_order_state.sql";

    @Bean
    FlywayMigrationStrategy erpV90MigrationStrategy() {
        return flyway -> {
            // MySQL DDL in V90 is committed before the later JOIN fails. Flyway then refuses
            // every startup until the already applied part is completed and marked successful.
            if (recoverIfFailed(flyway)) {
                flyway.migrate();
                return;
            }
            try {
                flyway.migrate();
            } catch (FlywayException error) {
                if (!isCollationError(error) || !recoverIfFailed(flyway)) throw error;
                flyway.migrate();
            }
        };
    }

    private static boolean recoverIfFailed(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            if (!historyExists(connection) || !failedV90(connection)) return false;
            // Never repair an unrelated failure or a V90 that stopped before its DDL completed.
            if (!tableExists(connection, "crm_erp_order_state") || !indexExists(connection, "crm_erp_unmapped", "uk_erp_unmapped_inbox")
                    || !differentOrderCollations(connection))
                throw new IllegalStateException("ERP V90 failed in an unexpected state; manual migration review is required");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/recovery/erp_v90_partial.sql"));
            try (Statement statement = connection.createStatement()) {
                if (statement.executeUpdate("UPDATE flyway_schema_history SET success=1 WHERE version='90' AND script='" + SCRIPT + "' AND success=0") != 1)
                    throw new IllegalStateException("ERP V90 recovery lost the migration history guard");
            }
            log.warn("Completed the partial ERP V90 migration after a MySQL collation conflict");
            return true;
        } catch (SQLException error) {
            throw new IllegalStateException("ERP V90 recovery could not check migration metadata", error);
        }
    }

    private static boolean historyExists(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "flyway_schema_history", new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private static boolean failedV90(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version='90' AND script='" + SCRIPT + "' AND success=0")) {
            rows.next();
            return rows.getInt(1) == 1;
        }
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getInt(1) == 1; }
        }
    }

    private static boolean indexExists(Connection connection, String table, String index) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?")) {
            statement.setString(1, table); statement.setString(2, index);
            try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getInt(1) > 0; }
        }
    }

    private static boolean differentOrderCollations(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT COUNT(DISTINCT collation_name) FROM information_schema.columns WHERE table_schema=DATABASE() "
                        + "AND table_name IN ('crm_erp_unmapped','crm_erp_order_state') AND column_name='order_no'")) {
            rows.next(); return rows.getInt(1) == 2;
        }
    }

    private static boolean isCollationError(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1267) return true;
        return false;
    }
}
