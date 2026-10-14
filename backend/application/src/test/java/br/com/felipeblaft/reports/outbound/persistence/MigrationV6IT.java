package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V20: a V6 é aditiva. Aplica as migrações até a V5 num esquema próprio, grava linhas "antigas" em
 * {@code update_telegram_processado}, aplica a V6 e confirma que nada se perde, que as restrições
 * novas valem e que o registro atômico do webhook antigo continua funcionando.
 */
class MigrationV6IT {

    private static final String SCHEMA = "migracao_v6_teste";

    private final String url =
            System.getenv()
                    .getOrDefault(
                            "DATABASE_URL", "jdbc:postgresql://localhost:5432/relatorios_teste");
    private final String user = System.getenv().getOrDefault("DATABASE_USER", "relatorios");
    private final String password = System.getenv().getOrDefault("DATABASE_PASSWORD", "teste");

    private Flyway flyway(String target) {
        var config =
                Flyway.configure()
                        .dataSource(url, user, password)
                        .schemas(SCHEMA)
                        .defaultSchema(SCHEMA)
                        .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setSchema(SCHEMA);
        return connection;
    }

    private void run(String sql) throws SQLException {
        try (Connection c = connection();
                Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private int update(String sql) throws SQLException {
        try (Connection c = connection();
                Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        }
    }

    @BeforeEach
    @AfterEach
    void cleanSchema() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password);
                Statement s = c.createStatement()) {
            s.execute("drop schema if exists " + SCHEMA + " cascade");
        }
    }

    @Test
    void v6PreservesOldRowsAndAppliesNewRestrictions() throws Exception {
        flyway("5").migrate();
        run(
                "insert into update_telegram_processado (update_id, recebido_em)"
                        + " values (11, now()), (12, now())");

        var result = flyway("6").migrate();
        assertThat(result.migrationsExecuted).isEqualTo(1);

        try (Connection c = connection();
                Statement s = c.createStatement();
                var rows =
                        s.executeQuery(
                                "select update_id, total_acoes, origem from update_telegram_processado"
                                        + " order by update_id")) {
            int n = 0;
            while (rows.next()) {
                n++;
                assertThat(rows.getObject("total_acoes")).isNull();
                assertThat(rows.getString("origem")).isNull();
            }
            assertThat(n).isEqualTo(2);
        }

        // restrições novas
        assertThatThrownBy(
                        () ->
                                update(
                                        "insert into update_telegram_processado"
                                                + " (update_id, recebido_em, origem) values (20, now(), 'OUTRO')"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_update_telegram_origem");
        assertThatThrownBy(
                        () ->
                                update(
                                        "insert into update_telegram_processado"
                                                + " (update_id, recebido_em, total_acoes) values (21, now(), -1)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_update_telegram_total_acoes");
        assertThat(
                        update(
                                "insert into update_telegram_processado"
                                        + " (update_id, recebido_em, origem, total_acoes)"
                                        + " values (22, now(), 'INTERNO', 0)"))
                .isEqualTo(1);
    }

    @Test
    void oldWebhookAtomicRegistrationStaysEqual() throws Exception {
        flyway("6").migrate();
        String insertion =
                "insert into update_telegram_processado (update_id, recebido_em)"
                        + " values (99, now()) on conflict (update_id) do nothing";
        assertThat(update(insertion)).isEqualTo(1);
        assertThat(update(insertion)).isZero();
    }

    private String action(String id, String state, String type, String origin, String updateId) {
        return "insert into acao_canal (id, lote_id, canal, origem, update_id, chat_id, tipo,"
                + " estado, tentativas, prazo_em, criado_em) values ('"
                + id
                + "', 'u1', 'TELEGRAM', '"
                + origin
                + "', "
                + updateId
                + ", 7, '"
                + type
                + "', '"
                + state
                + "', 0, now(), now())";
    }

    @Test
    void outboxHasIdentitySequenceAndRestrictions() throws Exception {
        flyway("6").migrate();
        assertThat(update(action("u1-0", "ENTREGUE", "MENSAGEM", "RESPOSTA_UPDATE", "1")))
                .isEqualTo(1);
        assertThat(update(action("u1-1", "PENDENTE", "EDICAO", "PROATIVA", "null"))).isEqualTo(1);
        try (Connection c = connection();
                Statement s = c.createStatement();
                var r =
                        s.executeQuery(
                                "select seq, versao, tentativas from acao_canal order by seq")) {
            assertThat(r.next()).isTrue();
            long first = r.getLong("seq");
            assertThat(r.getInt("versao")).isZero();
            assertThat(r.next()).isTrue();
            assertThat(r.getLong("seq")).isGreaterThan(first);
        }
        assertThatThrownBy(() -> update(action("x", "INVENTADO", "MENSAGEM", "PROATIVA", "null")))
                .hasMessageContaining("ck_acao_canal_estado");
        assertThatThrownBy(() -> update(action("x", "PENDENTE", "AUDIO", "PROATIVA", "null")))
                .hasMessageContaining("ck_acao_canal_tipo");
        assertThatThrownBy(() -> update(action("x", "PENDENTE", "MENSAGEM", "OUTRA", "null")))
                .hasMessageContaining("ck_acao_canal_origem");
        assertThatThrownBy(
                        () ->
                                update(
                                        action(
                                                "x",
                                                "ENTREGUE",
                                                "MENSAGEM",
                                                "RESPOSTA_UPDATE",
                                                "null")))
                .hasMessageContaining("ck_acao_canal_resposta_update");
        assertThatThrownBy(() -> update(action("u1-0", "PENDENTE", "MENSAGEM", "PROATIVA", "null")))
                .hasMessageContaining("acao_canal_pkey");
        assertThatThrownBy(() -> run("update acao_canal set seq = 99"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void channelDocumentHasRestrictions() throws Exception {
        flyway("6").migrate();
        String base =
                "insert into documento_canal (token_hash, professor_id, relatorio_tipo, relatorio_id,"
                        + " versao, nome_arquivo, criado_em, expira_em) values ('%s', '%s', '%s', '%s', %s,"
                        + " 'r.pdf', now(), now())";
        String hash = "a".repeat(64);
        UUID id = UUID.randomUUID();
        assertThat(update(String.format(base, hash, id, "AULA", id, "null"))).isEqualTo(1);
        assertThatThrownBy(
                        () -> update(String.format(base, "b".repeat(64), id, "OUTRO", id, "null")))
                .hasMessageContaining("ck_documento_canal_tipo");
        assertThatThrownBy(() -> update(String.format(base, "c".repeat(64), id, "AULA", id, "0")))
                .hasMessageContaining("ck_documento_canal_versao");
        assertThatThrownBy(() -> update(String.format(base, hash, id, "AULA", id, "1")))
                .hasMessageContaining("documento_canal_pkey");
    }
}
