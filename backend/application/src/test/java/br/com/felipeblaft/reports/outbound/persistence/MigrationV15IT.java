package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.support.MigrationTestSupport;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Feature 005, {@code V15__channel_action_delete_message.sql}: antes da migração o banco recusa o
 * tipo {@code DELETE_MESSAGE}; depois aceita, sem mexer nas linhas existentes, no nome da restrição
 * nem nos tipos anteriores. Prova as duas rotas: banco parado na {@code V14} (a versão anterior da
 * {@code main}) e banco novo, migrado de uma só vez da {@code V1} à última.
 */
class MigrationV15IT {

    private static final String SCHEMA = "migration_v15_test";

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);

    @BeforeEach
    void startAtV14() throws Exception {
        db.dropSchema();
        db.flyway("14").migrate();
    }

    @AfterEach
    void cleanSchema() throws SQLException {
        db.dropSchema();
    }

    private int channelAction(String type) throws SQLException {
        return db.update(
                "insert into channel_action (id, batch_id, channel, origin, chat_id, type, state,"
                        + " deadline_at, created_at) values ('"
                        + UUID.randomUUID()
                        + "', 'u1', 'TELEGRAM', 'PROACTIVE', 7, '"
                        + type
                        + "', 'PENDING', now() + interval '1 day', now())");
    }

    @Test
    void beforeV15TheDatabaseRefusesTheNewType() {
        assertThatThrownBy(() -> channelAction("DELETE_MESSAGE"))
                .hasMessageContaining("ck_channel_action_type");
    }

    @Test
    void afterV15TheNewTypeIsAcceptedAndExistingRowsSurvive() throws Exception {
        assertThat(channelAction("STEP_MESSAGE")).isEqualTo(1);

        db.flyway(null).migrate();

        assertThat(db.single("select count(*) from channel_action")).isEqualTo("1");
        assertThat(channelAction("DELETE_MESSAGE")).isEqualTo(1);
        for (String type : new String[] {"MESSAGE", "EDIT", "DOCUMENT", "CALLBACK_REPLY"}) {
            assertThat(channelAction(type)).as(type).isEqualTo(1);
        }
        assertThatThrownBy(() -> channelAction("DELETE_CHAT"))
                .hasMessageContaining("ck_channel_action_type");
    }

    @Test
    void aNewDatabaseMigratesFromScratchAndAcceptsTheNewType() throws Exception {
        db.dropSchema();

        db.flyway(null).migrate();

        assertThat(db.single("select max(version::int) from flyway_schema_history"))
                .isEqualTo("15");
        assertThat(channelAction("DELETE_MESSAGE")).isEqualTo(1);
        assertThatThrownBy(() -> channelAction("DELETE_CHAT"))
                .hasMessageContaining("ck_channel_action_type");
    }

    @Test
    void v15OnlyReplacesTheTypeConstraintKeepingItsName() throws Exception {
        MigrationTestSupport.Catalog before = db.catalog();

        db.flyway(null).migrate();

        MigrationTestSupport.Catalog after = db.catalog();
        assertThat(after.columns()).isEqualTo(before.columns());
        assertThat(after.indexes()).isEqualTo(before.indexes());
        assertThat(after.sequences()).isEqualTo(before.sequences());
        assertThat(after.constraints().keySet()).isEqualTo(before.constraints().keySet());
        Map<String, String> changed = new java.util.TreeMap<>(after.constraints());
        changed.entrySet().removeIf(e -> e.getValue().equals(before.constraints().get(e.getKey())));
        assertThat(changed.keySet()).containsExactly("channel_action.ck_channel_action_type");
        assertThat(changed.get("channel_action.ck_channel_action_type")).contains("DELETE_MESSAGE");
    }
}
