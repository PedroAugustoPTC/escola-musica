package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.support.MigrationTestSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Feature 007, R-03: a aplicação usa o papel {@code reports_app}, que não altera nem apaga a
 * trilha, não muda o esquema e só chama o expurgo pela função com piso de 365 dias. O Flyway e o
 * dono seguem com o papel de migração.
 */
class DatabaseRolesIT {

    private static final String SCHEMA = "database_roles_test";
    private static final String APP_PASSWORD = "roles-test-password";

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);
    private final String url =
            System.getenv()
                    .getOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5432/reports_test");

    @BeforeEach
    void migrateAndEnableTheApplicationRole() throws Exception {
        db.dropSchema();
        db.flyway(null).migrate();
        db.execute("alter role reports_app login password '" + APP_PASSWORD + "'");
    }

    @AfterEach
    void cleanSchema() throws SQLException {
        db.dropSchema();
    }

    private Connection asApplication() throws SQLException {
        Connection connection = DriverManager.getConnection(url, "reports_app", APP_PASSWORD);
        connection.setSchema(SCHEMA);
        return connection;
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = asApplication();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void appendAuthorship(String operation) throws SQLException {
        execute(
                "insert into authorship_record (id, occurred_at, origin, operation, result, correlation_id,"
                        + " previous_hash, record_hash) values ('"
                        + UUID.randomUUID()
                        + "', now() - interval '800 days', 'WEB', '"
                        + operation
                        + "', 'SUCCESS', 'req-1', '"
                        + AuditChain.GENESIS_HASH
                        + "', '"
                        + "3".repeat(64)
                        + "')");
    }

    @Test
    void applicationRoleCanAppendAndReadTheTrail() throws Exception {
        appendAuthorship("ADMIN_SIGN_OUT");

        execute("select count(*) from authorship_record");
        assertThat(db.single("select count(*) from authorship_record")).isEqualTo("1");
    }

    @Test
    void applicationRoleCannotChangeOrDeleteTheTrail() throws Exception {
        appendAuthorship("ADMIN_SIGN_OUT");

        assertThatThrownBy(() -> execute("update authorship_record set result = 'FAILURE'"))
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> execute("delete from authorship_record"))
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> execute("truncate authorship_record"))
                .hasMessageContaining("permission denied");
    }

    @Test
    void applicationRoleCannotDisableTriggersOrChangeTheSchema() {
        assertThatThrownBy(
                        () ->
                                execute(
                                        "alter table authorship_record disable trigger"
                                                + " tr_authorship_record_no_change"))
                .hasMessageContaining("must be owner");
        assertThatThrownBy(() -> execute("create table intruder (id int)"))
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> execute("drop table audit_chain_anchor"))
                .hasMessageContaining("must be owner");
    }

    @Test
    void applicationRoleKeepsFullAccessToOperationalTables() throws Exception {
        UUID id = UUID.randomUUID();
        execute(
                "insert into teacher (id, name, email, active, created_at, updated_at) values ('"
                        + id
                        + "', 'Clara', 'clara@example.test', true, now(), now())");
        execute("update teacher set name = 'Clara M.' where id = '" + id + "'");
        execute("delete from teacher where id = '" + id + "'");
    }

    @Test
    void applicationRoleCannotUpdateTheMigrationHistory() {
        assertThatThrownBy(() -> execute("select count(*) from flyway_schema_history"))
                .hasMessageContaining("permission denied");
    }

    @Test
    void purgeFunctionRefusesACutoffInsideTheFloor() throws Exception {
        appendAuthorship("ADMIN_SIGN_OUT");

        assertThatThrownBy(
                        () -> execute("select purge_authorship_record(now() - interval '30 days')"))
                .hasMessageContaining("piso de 365 dias");
        assertThat(db.single("select count(*) from authorship_record")).isEqualTo("1");
    }

    @Test
    void purgeFunctionRemovesThePrefixAndWritesTheAnchor() throws Exception {
        appendAuthorship("ADMIN_SIGN_OUT");

        execute("select purge_authorship_record(now() - interval '400 days')");

        assertThat(db.single("select count(*) from authorship_record")).isEqualTo("0");
        assertThat(db.single("select purged_count from audit_chain_anchor")).isEqualTo("1");
        assertThat(db.single("select last_record_hash from audit_chain_anchor"))
                .isEqualTo("3".repeat(64));
    }
}
