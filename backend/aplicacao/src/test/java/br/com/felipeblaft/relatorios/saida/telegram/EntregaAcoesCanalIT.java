package br.com.felipeblaft.relatorios.saida.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.canal.EntregarAcoesPendentesCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.EntregaOrquestradorBase;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * V8 a V11, C9, C11, SC-004: entrega proativa ao orquestrador (WireMock) com retentativa, ordem por
 * chat, portão de confirmação, idempotência e métricas. O tempo é do {@code RelogioAjustavel}; o
 * despachante agendado fica desligado (intervalo de 1 h) e os ciclos são chamados pelo teste.
 */
@ExtendWith(OutputCaptureExtension.class)
class EntregaAcoesCanalIT extends EntregaOrquestradorBase {

    private double gauge(String nome) {
        return metricas.get(nome).gauge().value();
    }

    /** Os contadores são do contexto compartilhado: cada teste enxerga só o que ele mesmo somou. */
    private final java.util.Map<String, Double> linhaDeBase = new java.util.HashMap<>();

    private static String chave(String nome, String... tags) {
        return nome + java.util.Arrays.toString(tags);
    }

    private double total(String nome, String... tags) {
        var busca = metricas.find(nome).tags(tags).counter();
        return busca == null ? 0 : busca.count();
    }

    @org.junit.jupiter.api.BeforeEach
    void marcarLinhaDeBase() {
        for (String[] contador :
                new String[][] {
                    {"canal_entregas_total", "resultado", "aceita"},
                    {"canal_entregas_total", "resultado", "recusada"},
                    {"canal_acoes_falha_definitiva_total"},
                    {"canal_confirmacoes_total", "situacao", "FALHOU", "codigo", "ERRO_TELEGRAM"}
                }) {
            String[] tags = java.util.Arrays.copyOfRange(contador, 1, contador.length);
            linhaDeBase.put(chave(contador[0], tags), total(contador[0], tags));
        }
    }

    private double contador(String nome, String... tags) {
        return total(nome, tags) - linhaDeBase.getOrDefault(chave(nome, tags), 0.0);
    }

    private void confirmarTudoPeloExecutor(JsonNode lote) throws Exception {
        executorDoOrquestrador().executar(lote.get("acoes"));
    }

    // ---- V8 / C9: orquestrador fora do ar e volta

    @Test
    void foraDoArFicaPendenteReenviaComOsMesmosIdsEmIntervalosCrescentesEEntregaAoVoltar()
            throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        responder(503);

        gerarAvisoDe(matteo);

        String acaoId = idsDasAcoes().get(0);
        assertThat(acaoId).startsWith("p:resultado:");
        tarefa.despachar();
        assertThat(estado(acaoId)).isEqualTo("PENDENTE");
        // o processamento do relatório não depende do orquestrador (FR-015)
        assertThat(jdbc.queryForObject("select status from relatorio_aula", String.class))
                .isEqualTo("AGUARDANDO_RESPOSTAS");
        Instant primeira =
                jdbc.queryForObject("select proxima_tentativa_em from acao_canal", Timestamp.class)
                        .toInstant();
        // 30 s ±10%
        assertThat(Duration.between(relogio.agora(), primeira).toSeconds()).isBetween(26L, 34L);

        relogio.avancar(Duration.ofSeconds(35));
        tarefa.despachar();
        Instant segunda =
                jdbc.queryForObject("select proxima_tentativa_em from acao_canal", Timestamp.class)
                        .toInstant();
        assertThat(Duration.between(relogio.agora(), segunda).toSeconds()).isBetween(53L, 67L);

        relogio.avancar(Duration.ofSeconds(70));
        aceitar();
        tarefa.despachar();

        assertThat(estado(acaoId)).isEqualTo("ENTREGUE");
        List<LoggedRequest> recebidas = entregas();
        assertThat(recebidas).hasSize(3);
        List<String> idsEnviados = new ArrayList<>();
        List<Integer> tentativas = new ArrayList<>();
        for (LoggedRequest entrega : recebidas) {
            JsonNode lote = corpo(entrega);
            assertThat(lote.path("origem").asString()).isEqualTo("PROATIVA");
            assertThat(entrega.getHeader("Idempotency-Key"))
                    .isEqualTo(lote.path("loteId").asString());
            assertThat(entrega.getHeader("X-Canal-Entrega-Token"))
                    .isEqualTo("segredo-do-orquestrador");
            idsEnviados.add(lote.get("acoes").get(0).path("acaoId").asString());
            tentativas.add(lote.path("tentativa").asInt());
        }
        assertThat(idsEnviados).containsOnly(acaoId);
        assertThat(tentativas).containsExactly(1, 2, 3);
        assertThat(contador("canal_entregas_total", "resultado", "recusada")).isEqualTo(2);
        assertThat(contador("canal_entregas_total", "resultado", "aceita")).isEqualTo(1);
    }

    @Test
    void recusaTimeoutEErroDeConexaoContinuamPendentesAteOPrazo(CapturedOutput saida)
            throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        String acaoId = idsDasAcoes().get(0);

        responder(500);
        tarefa.despachar();
        assertThat(estado(acaoId)).isEqualTo("PENDENTE");

        relogio.avancar(Duration.ofMinutes(1));
        responder(404);
        tarefa.despachar();
        assertThat(estado(acaoId)).isEqualTo("PENDENTE");

        relogio.avancar(Duration.ofMinutes(5));
        responderComAtraso(200, 4000); // além do timeout de resposta (1,5 s)
        tarefa.despachar();
        assertThat(estado(acaoId)).isEqualTo("PENDENTE");

        relogio.avancar(Duration.ofMinutes(20));
        ORQUESTRADOR.resetMappings();
        ORQUESTRADOR.stubFor(
                post(urlPathEqualTo(CAMINHO))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        tarefa.despachar();
        assertThat(estado(acaoId)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("select tentativas from acao_canal", Integer.class))
                .isEqualTo(4);

        // depois de 24 h sem aceite: falha definitiva, sem dado de aluno no log e com métrica
        relogio.avancar(Duration.ofHours(25));
        aceitar();
        tarefa.despachar();

        assertThat(estado(acaoId)).isEqualTo("FALHA_DEFINITIVA");
        assertThat(jdbc.queryForObject("select codigo_motivo from acao_canal", String.class))
                .isEqualTo("PRAZO_VENCIDO");
        assertThat(contador("canal_acoes_falha_definitiva_total")).isEqualTo(1);
        assertThat(saida.getOut() + saida.getErr())
                .contains("canal-acao-falha-definitiva acaoId=" + acaoId)
                .doesNotContain("Matteo", "perguntas pendentes")
                .doesNotContain("segredo-do-orquestrador", "segredo-no-caminho-123");
        assertThat(entregas()).hasSize(4);
    }

    // ---- V9: ordem por chat, portão de confirmação, chats independentes

    @Test
    void loteDoMesmoChatEsperaAConfirmacaoOuOTimeoutEChatsDistintosSeguemEmParalelo()
            throws Exception {
        Professor bruno = cenario.bruno();
        Aluno davi = cenario.aluno(bruno, "Davi Nunes", "Bateria");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        vincular(USUARIO_BRUNO, CenarioIT.CODIGO_BRUNO);
        gerarAvisoDe(matteo);
        gerarAvisoDe(helena);
        registrar.executar(bruno.id(), davi.id(), audioDaWeb(), Canal.WEB);
        cenario.processarFila();
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class))
                .isEqualTo(3);

        tarefa.despachar();

        // um lote de Clara e o de Bruno; o segundo lote de Clara espera
        List<LoggedRequest> primeiras = entregas();
        assertThat(primeiras).hasSize(2);
        List<Long> chats = new ArrayList<>();
        for (LoggedRequest entrega : primeiras) {
            chats.add(corpo(entrega).get("acoes").get(0).path("chatId").asLong());
        }
        assertThat(chats).containsExactlyInAnyOrder(USUARIO_CLARA, USUARIO_BRUNO);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from acao_canal where estado = 'PENDENTE'",
                                Integer.class))
                .isEqualTo(1);

        // o n8n confirma o primeiro lote de Clara: o segundo é liberado antes do timeout
        JsonNode primeiroDeClara = null;
        for (LoggedRequest entrega : primeiras) {
            JsonNode lote = corpo(entrega);
            if (lote.get("acoes").get(0).path("chatId").asLong() == USUARIO_CLARA) {
                primeiroDeClara = lote;
            }
        }
        confirmarTudoPeloExecutor(primeiroDeClara);
        tarefa.despachar();
        assertThat(entregas()).hasSize(3);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from acao_canal where estado = 'PENDENTE'",
                                Integer.class))
                .isZero();
    }

    @Test
    void semConfirmacaoOPortaoLiberaDepoisDe60Segundos() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        gerarAvisoDe(helena);

        tarefa.despachar();
        assertThat(entregas()).hasSize(1);

        relogio.avancar(Duration.ofSeconds(59));
        tarefa.despachar();
        assertThat(entregas()).hasSize(1);

        relogio.avancar(Duration.ofSeconds(2));
        tarefa.despachar();
        assertThat(entregas()).hasSize(2);
        // métrica de ações entregues e ainda sem confirmação além da espera máxima
        relogio.avancar(Duration.ofSeconds(5));
        assertThat(gauge("canal_acoes_sem_confirmacao_total")).isEqualTo(1);
    }

    // ---- V10: idempotência do evento, concorrência, vínculo

    @Test
    void notificarOMesmoTrabalhoDuasVezesGeraUmaSoAcao() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        var trabalho =
                fila.buscarPorId(
                                jdbc.queryForObject(
                                        "select id from trabalho_processamento", UUID.class))
                        .orElseThrow();

        notificacao.notificarResultado(trabalho);
        notificacao.notificarResultado(trabalho);

        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void doisDespachantesConcorrentesEntregamOLoteUmaSoVez() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        responderComAtraso(200, 600);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<EntregarAcoesPendentesCasoDeUso.Ciclo>> ciclos = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ciclos.add(
                    pool.submit(
                            () -> {
                                largada.await();
                                return entregar.executar(20);
                            }));
        }
        largada.countDown();
        int aceitos = 0;
        for (Future<EntregarAcoesPendentesCasoDeUso.Ciclo> ciclo : ciclos) {
            aceitos += ciclo.get().lotesAceitos();
        }
        pool.shutdown();

        assertThat(aceitos).isEqualTo(1);
        assertThat(entregas()).hasSize(1);
    }

    @Test
    void vinculoDesfeitoDescartaAAcaoSemEntrega() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        jdbc.update("delete from vinculo_telegram");

        tarefa.despachar();

        String acaoId = idsDasAcoes().get(0);
        assertThat(estado(acaoId)).isEqualTo("DESCARTADA");
        assertThat(jdbc.queryForObject("select codigo_motivo from acao_canal", String.class))
                .isEqualTo("VINCULO_DESFEITO");
        assertThat(entregas()).isEmpty();
    }

    // ---- V11: corrida e reinício

    @Test
    void confirmacaoQueChegaDuranteOHttpDoDespachoPrevalece() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        String acaoId = idsDasAcoes().get(0);
        responderComAtraso(200, 1000);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> confirmacao =
                pool.submit(
                        () -> {
                            Thread.sleep(300);
                            mvc.perform(
                                    org.springframework.test.web.servlet.request
                                            .MockMvcRequestBuilders.post(
                                                    "/interno/v1/canal/telegram/confirmacoes")
                                            .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    "{\"versao\":\"v1\",\"resultados\":[{\"acaoId\":\""
                                                            + acaoId
                                                            + "\",\"situacao\":\"EXECUTADA\"}]}"));
                            return null;
                        });

        tarefa.despachar();
        confirmacao.get();
        pool.shutdown();

        assertThat(estado(acaoId)).isEqualTo("EXECUTADA");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from acao_canal where estado = 'ENTREGUE'",
                                Integer.class))
                .isZero();
    }

    @Test
    void despachanteQueMorreDeixaOLoteReentregavelComOsMesmosIdsQuandoOLeaseVence()
            throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        String acaoId = idsDasAcoes().get(0);
        // outro despachante reservou o lote (lease de 60 s) e morreu antes de entregar
        assertThat(
                        acoes.reservarProximoLoteDevido(
                                relogio.agora(),
                                Duration.ofSeconds(60),
                                relogio.agora().plusSeconds(60)))
                .hasSize(1);

        tarefa.despachar();
        assertThat(entregas()).isEmpty();

        relogio.avancar(Duration.ofSeconds(61));
        tarefa.despachar();

        assertThat(entregas()).hasSize(1);
        assertThat(corpo(entregas().get(0)).get("acoes").get(0).path("acaoId").asString())
                .isEqualTo(acaoId);
    }

    // ---- idempotência do receptor (FR-013, SC-004)

    @Test
    void omesmoLoteEntregueDuasVezesAoReceptorExecutaCadaAcaoUmaSoVez() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        responder(500);
        tarefa.despachar();
        relogio.avancar(Duration.ofMinutes(1));
        aceitar();
        tarefa.despachar();
        API_BOTS.resetRequests();
        JsonNode lote = corpo(entregas().getLast());

        confirmarTudoPeloExecutor(lote);
        confirmarTudoPeloExecutor(lote); // reentrega do mesmo lote (ex.: n8n reiniciou)

        assertThat(chamadas("sendMessage")).hasSize(1);
        assertThat(textosEnviados().get(0))
                .isEqualTo("O relatório de Matteo Ferreira tem perguntas pendentes.");
        assertThat(estado(idsDasAcoes().get(0))).isEqualTo("EXECUTADA");
    }

    // ---- métricas

    @Test
    void metricasDaOutboxEDasConfirmacoes() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        gerarAvisoDe(helena);

        assertThat(gauge("canal_acoes_pendentes")).isEqualTo(2);
        relogio.avancar(Duration.ofSeconds(90));
        assertThat(gauge("canal_acao_pendente_mais_antiga_segundos")).isEqualTo(90);

        tarefa.despachar();
        assertThat(gauge("canal_acoes_pendentes")).isEqualTo(1);
        assertThat(contador("canal_entregas_total", "resultado", "aceita")).isEqualTo(1);

        String entregue =
                jdbc.queryForObject(
                        "select id from acao_canal where estado = 'ENTREGUE'", String.class);
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/interno/v1/canal/telegram/confirmacoes")
                        .header("X-N8N-Service-Token", TOKEN_SERVICO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"versao\":\"v1\",\"resultados\":[{\"acaoId\":\""
                                        + entregue
                                        + "\",\"situacao\":\"FALHOU\",\"codigoMotivo\":\"CODIGO_NOVO_QUALQUER\"}]}"));
        assertThat(
                        contador(
                                "canal_confirmacoes_total",
                                "situacao",
                                "FALHOU",
                                "codigo",
                                "ERRO_TELEGRAM"))
                .isEqualTo(1);
    }
}
