package br.com.felipeblaft.relatorios.saida.persistencia;

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
class MigracaoV6IT {

    private static final String ESQUEMA = "migracao_v6_teste";

    private final String url =
            System.getenv()
                    .getOrDefault("BANCO_URL", "jdbc:postgresql://localhost:5432/relatorios_teste");
    private final String usuario = System.getenv().getOrDefault("BANCO_USUARIO", "relatorios");
    private final String senha = System.getenv().getOrDefault("BANCO_SENHA", "teste");

    private Flyway flyway(String alvo) {
        var configuracao =
                Flyway.configure()
                        .dataSource(url, usuario, senha)
                        .schemas(ESQUEMA)
                        .defaultSchema(ESQUEMA)
                        .locations("classpath:db/migration");
        if (alvo != null) {
            configuracao.target(alvo);
        }
        return configuracao.load();
    }

    private Connection conexao() throws SQLException {
        Connection conexao = DriverManager.getConnection(url, usuario, senha);
        conexao.setSchema(ESQUEMA);
        return conexao;
    }

    private void executar(String sql) throws SQLException {
        try (Connection c = conexao();
                Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private int atualizar(String sql) throws SQLException {
        try (Connection c = conexao();
                Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        }
    }

    @BeforeEach
    @AfterEach
    void limparEsquema() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, usuario, senha);
                Statement s = c.createStatement()) {
            s.execute("drop schema if exists " + ESQUEMA + " cascade");
        }
    }

    @Test
    void v6PreservaLinhasAntigasEAplicaAsRestricoesNovas() throws Exception {
        flyway("5").migrate();
        executar(
                "insert into update_telegram_processado (update_id, recebido_em)"
                        + " values (11, now()), (12, now())");

        var resultado = flyway(null).migrate();
        assertThat(resultado.migrationsExecuted).isEqualTo(1);

        try (Connection c = conexao();
                Statement s = c.createStatement();
                var linhas =
                        s.executeQuery(
                                "select update_id, total_acoes, origem from update_telegram_processado"
                                        + " order by update_id")) {
            int n = 0;
            while (linhas.next()) {
                n++;
                assertThat(linhas.getObject("total_acoes")).isNull();
                assertThat(linhas.getString("origem")).isNull();
            }
            assertThat(n).isEqualTo(2);
        }

        // restrições novas
        assertThatThrownBy(
                        () ->
                                atualizar(
                                        "insert into update_telegram_processado"
                                                + " (update_id, recebido_em, origem) values (20, now(), 'OUTRO')"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_update_telegram_origem");
        assertThatThrownBy(
                        () ->
                                atualizar(
                                        "insert into update_telegram_processado"
                                                + " (update_id, recebido_em, total_acoes) values (21, now(), -1)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_update_telegram_total_acoes");
        assertThat(
                        atualizar(
                                "insert into update_telegram_processado"
                                        + " (update_id, recebido_em, origem, total_acoes)"
                                        + " values (22, now(), 'INTERNO', 0)"))
                .isEqualTo(1);
    }

    @Test
    void registroAtomicoDoWebhookAntigoContinuaIgual() throws Exception {
        flyway(null).migrate();
        String insercao =
                "insert into update_telegram_processado (update_id, recebido_em)"
                        + " values (99, now()) on conflict (update_id) do nothing";
        assertThat(atualizar(insercao)).isEqualTo(1);
        assertThat(atualizar(insercao)).isZero();
    }

    private String acao(String id, String estado, String tipo, String origem, String updateId) {
        return "insert into acao_canal (id, lote_id, canal, origem, update_id, chat_id, tipo,"
                + " estado, tentativas, prazo_em, criado_em) values ('"
                + id
                + "', 'u1', 'TELEGRAM', '"
                + origem
                + "', "
                + updateId
                + ", 7, '"
                + tipo
                + "', '"
                + estado
                + "', 0, now(), now())";
    }

    @Test
    void outboxTemSequenciaIdentidadeERestricoes() throws Exception {
        flyway(null).migrate();
        assertThat(atualizar(acao("u1-0", "ENTREGUE", "MENSAGEM", "RESPOSTA_UPDATE", "1")))
                .isEqualTo(1);
        assertThat(atualizar(acao("u1-1", "PENDENTE", "EDICAO", "PROATIVA", "null"))).isEqualTo(1);
        try (Connection c = conexao();
                Statement s = c.createStatement();
                var r =
                        s.executeQuery(
                                "select seq, versao, tentativas from acao_canal order by seq")) {
            assertThat(r.next()).isTrue();
            long primeira = r.getLong("seq");
            assertThat(r.getInt("versao")).isZero();
            assertThat(r.next()).isTrue();
            assertThat(r.getLong("seq")).isGreaterThan(primeira);
        }
        assertThatThrownBy(() -> atualizar(acao("x", "INVENTADO", "MENSAGEM", "PROATIVA", "null")))
                .hasMessageContaining("ck_acao_canal_estado");
        assertThatThrownBy(() -> atualizar(acao("x", "PENDENTE", "AUDIO", "PROATIVA", "null")))
                .hasMessageContaining("ck_acao_canal_tipo");
        assertThatThrownBy(() -> atualizar(acao("x", "PENDENTE", "MENSAGEM", "OUTRA", "null")))
                .hasMessageContaining("ck_acao_canal_origem");
        assertThatThrownBy(
                        () ->
                                atualizar(
                                        acao(
                                                "x",
                                                "ENTREGUE",
                                                "MENSAGEM",
                                                "RESPOSTA_UPDATE",
                                                "null")))
                .hasMessageContaining("ck_acao_canal_resposta_update");
        assertThatThrownBy(
                        () -> atualizar(acao("u1-0", "PENDENTE", "MENSAGEM", "PROATIVA", "null")))
                .hasMessageContaining("acao_canal_pkey");
        assertThatThrownBy(() -> executar("update acao_canal set seq = 99"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void documentoCanalTemRestricoes() throws Exception {
        flyway(null).migrate();
        String base =
                "insert into documento_canal (token_hash, professor_id, relatorio_tipo, relatorio_id,"
                        + " versao, nome_arquivo, criado_em, expira_em) values ('%s', '%s', '%s', '%s', %s,"
                        + " 'r.pdf', now(), now())";
        String hash = "a".repeat(64);
        UUID id = UUID.randomUUID();
        assertThat(atualizar(String.format(base, hash, id, "AULA", id, "null"))).isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                atualizar(
                                        String.format(
                                                base, "b".repeat(64), id, "OUTRO", id, "null")))
                .hasMessageContaining("ck_documento_canal_tipo");
        assertThatThrownBy(
                        () -> atualizar(String.format(base, "c".repeat(64), id, "AULA", id, "0")))
                .hasMessageContaining("ck_documento_canal_versao");
        assertThatThrownBy(() -> atualizar(String.format(base, hash, id, "AULA", id, "1")))
                .hasMessageContaining("documento_canal_pkey");
    }
}
