package com.myg.controlplane.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class CoreSchemaMigrationTest {

    @Test
    void migratesCoreSchemaOnEmptyDatabaseAndCanRerun() throws SQLException {
        String url = "jdbc:h2:mem:core-schema-empty;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        Flyway flyway = flyway(url);

        MigrateResult first = flyway.migrate();
        assertThat(first.migrationsExecuted).isEqualTo(1);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "agents")).isTrue();
            assertThat(tableExists(connection, "agent_commands")).isTrue();
            assertThat(tableExists(connection, "agent_command_parameters")).isTrue();
            assertThat(tableExists(connection, "chaos_runs")).isTrue();
            assertThat(tableExists(connection, "chaos_run_route_filters")).isTrue();
            assertThat(tableExists(connection, "run_assignments")).isTrue();
            assertThat(tableExists(connection, "experiment_definitions")).isTrue();
            assertThat(tableExists(connection, "latency_telemetry_snapshots")).isTrue();
            assertThat(tableExists(connection, "run_execution_reports")).isTrue();
            assertThat(tableExists(connection, "run_lifecycle_events")).isTrue();
            assertThat(tableExists(connection, "experiments")).isTrue();
            assertThat(tableExists(connection, "experiment_runs")).isTrue();
            assertThat(tableExists(connection, "fault_injection_actions")).isTrue();
            assertThat(tableExists(connection, "telemetry_snapshots")).isTrue();
            assertThat(tableExists(connection, "audit_events")).isTrue();

            assertThat(columnExists(connection, "chaos_runs", "error_code")).isTrue();

            assertThat(importedKeys(connection, "agent_commands"))
                    .contains("agents");
            assertThat(importedKeys(connection, "agent_command_parameters"))
                    .contains("agent_commands");
            assertThat(importedKeys(connection, "chaos_run_route_filters"))
                    .contains("chaos_runs");
            assertThat(importedKeys(connection, "run_execution_reports"))
                    .contains("chaos_runs");
            assertThat(importedKeys(connection, "experiment_runs"))
                    .contains("experiments", "agents", "dispatch_approvals");
            assertThat(importedKeys(connection, "run_assignments"))
                    .contains("chaos_runs", "agents");
            assertThat(importedKeys(connection, "fault_injection_actions"))
                    .contains("experiment_runs", "agents");
            assertThat(importedKeys(connection, "telemetry_snapshots"))
                    .contains("experiment_runs", "agents");
            assertThat(importedKeys(connection, "audit_events"))
                    .contains("experiments", "experiment_runs", "agents", "fault_injection_actions");

            Set<String> agentCommandIndexes = indexNames(connection, "agent_commands");
            assertThat(agentCommandIndexes).contains("idx_agent_commands_agent_status_created_at");

            Set<String> experimentRunIndexes = indexNames(connection, "experiment_runs");
            assertThat(experimentRunIndexes).contains("idx_experiment_runs_experiment_created_at");
            assertThat(experimentRunIndexes).contains("idx_experiment_runs_status_created_at");

            Set<String> telemetryIndexes = indexNames(connection, "telemetry_snapshots");
            assertThat(telemetryIndexes).contains("idx_telemetry_snapshots_run_captured_at");

            Set<String> chaosRunIndexes = indexNames(connection, "chaos_runs");
            assertThat(chaosRunIndexes).contains("idx_chaos_runs_experiment_status_started_at");

            Set<String> chaosRunRouteFilterIndexes = indexNames(connection, "chaos_run_route_filters");
            assertThat(chaosRunRouteFilterIndexes).contains("idx_chaos_run_route_filters_run_order");

            Set<String> runAssignmentIndexes = indexNames(connection, "run_assignments");
            assertThat(runAssignmentIndexes).contains("idx_run_assignments_run_status");

            Set<String> latencyTelemetryIndexes = indexNames(connection, "latency_telemetry_snapshots");
            assertThat(latencyTelemetryIndexes).contains("idx_latency_telemetry_snapshots_run_captured_at");

            Set<String> runExecutionReportIndexes = indexNames(connection, "run_execution_reports");
            assertThat(runExecutionReportIndexes).contains("idx_run_execution_reports_run_reported_at");

            Set<String> lifecycleIndexes = indexNames(connection, "run_lifecycle_events");
            assertThat(lifecycleIndexes).contains("idx_run_lifecycle_events_run_recorded_at");

            Set<String> auditIndexes = indexNames(connection, "audit_events");
            assertThat(auditIndexes).contains("idx_audit_events_run_occurred_at");
        }

        MigrateResult second = flyway.migrate();
        assertThat(second.migrationsExecuted).isZero();
    }

    @Test
    void baselinesLegacyLocalSchemaBeforeApplyingCoreMigration() throws SQLException {
        String url = "jdbc:h2:mem:core-schema-legacy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE agents (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        hostname VARCHAR(255) NOT NULL,
                        environment VARCHAR(255) NOT NULL,
                        region VARCHAR(255) NOT NULL,
                        registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        last_heartbeat_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE agent_capabilities (
                        agent_id UUID NOT NULL,
                        capability VARCHAR(255) NOT NULL,
                        PRIMARY KEY (agent_id, capability)
                    )
                    """);
        }

        Flyway flyway = flyway(url);
        MigrateResult result = flyway.migrate();
        assertThat(String.valueOf(result.initialSchemaVersion)).isEqualTo("0");

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(tableExists(connection, "experiments")).isTrue();
            assertThat(tableExists(connection, "experiment_runs")).isTrue();
            assertThat(tableExists(connection, "audit_events")).isTrue();
            assertThat(indexNames(connection, "agents")).contains("idx_agents_environment_last_heartbeat");
        }
    }

    private Flyway flyway(String url) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load();
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getTables(null, null, tableName.toUpperCase(Locale.ROOT), null)) {
            return resultSet.next();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getColumns(
                null,
                null,
                tableName.toUpperCase(Locale.ROOT),
                columnName.toUpperCase(Locale.ROOT)
        )) {
            return resultSet.next();
        }
    }

    private Set<String> importedKeys(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> importedTables = new HashSet<>();
        try (ResultSet resultSet = metadata.getImportedKeys(null, null, tableName.toUpperCase(Locale.ROOT))) {
            while (resultSet.next()) {
                importedTables.add(resultSet.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return importedTables;
    }

    private Set<String> indexNames(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> indexes = new HashSet<>();
        try (ResultSet resultSet = metadata.getIndexInfo(null, null, tableName.toUpperCase(Locale.ROOT), false, false)) {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                if (indexName != null) {
                    indexes.add(indexName.toLowerCase(Locale.ROOT));
                }
            }
        }
        return indexes;
    }
}
