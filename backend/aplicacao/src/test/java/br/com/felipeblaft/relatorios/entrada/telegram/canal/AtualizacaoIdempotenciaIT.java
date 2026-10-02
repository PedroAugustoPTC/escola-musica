package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.EsquemaAcoesV1;
import br.com.felipeblaft.relatorios.suporte.RelogioAjustavel;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * V6, C4 a C6 e US1.5: um update repetido não é reprocessado e devolve só as ações ainda não
 * confirmadas, com os mesmos ids; o estado da conversa já está gravado quando a resposta sai e o
 * backend não envia nada à API de bots ao responder (FR-010).
 */
class AtualizacaoIdempotenciaIT extends TelegramConversaBase {

    private static final String BASE = "/interno/v1/canal/telegram";

    private JsonNode atualizacao(String json) throws Exception {
        String corpo =
                mvc.perform(
                                post(BASE + "/atualizacao")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                        .header("X-Telegram-Bot-Api-Secret-Token", SEGREDO)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        EsquemaAcoesV1.exigir("RespostaAtualizacao", corpo);
        return JSON.readTree(corpo);
    }

    private void confirmar(String corpoResultados) throws Exception {
        mvc.perform(
                        post(BASE + "/confirmacoes")
                                .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"versao\":\"v1\",\"resultados\":["
                                                + corpoResultados
                                                + "]}"))
                .andExpect(status().isOk());
    }

    private static String ok(String id) {
        return "{\"acaoId\":\"" + id + "\",\"situacao\":\"EXECUTADA\"}";
    }

    /**
     * Toque de botão de professor não vinculado: RESPOSTA_CALLBACK e a mensagem de código
     * necessário.
     */
    private String callbackNaoVinculado(long update) {
        return """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": "v1:reg:",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                .formatted(update, update, USUARIO_CLARA, USUARIO_CLARA);
    }

    @Test
    void mesmoUpdateSemConfirmarDevolveAsMesmasAcoesComOsMesmosIds() throws Exception {
        long update = proximoUpdate();
        String json = callbackNaoVinculado(update);

        JsonNode primeira = atualizacao(json);
        JsonNode segunda = atualizacao(json);
        JsonNode terceira = atualizacao(json);

        assertThat(primeira.get("acoes")).hasSize(2);
        assertThat(segunda.get("acoes")).isEqualTo(primeira.get("acoes"));
        assertThat(terceira.get("acoes")).isEqualTo(primeira.get("acoes"));
        assertThat(primeira.get("acoes").get(0).path("tipo").asString())
                .isEqualTo("RESPOSTA_CALLBACK");
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select total_acoes from update_telegram_processado where update_id = ?",
                                Integer.class,
                                update))
                .isEqualTo(2);
    }

    @Test
    void depoisDeConfirmarTudoOUpdateRepetidoVoltaVazio() throws Exception {
        long update = proximoUpdate();
        String json = callbackNaoVinculado(update);
        atualizacao(json);
        confirmar(ok("u" + update + "-0") + "," + ok("u" + update + "-1"));

        JsonNode repetida = atualizacao(json);

        assertThat(repetida.get("acoes")).isEmpty();
        assertThat(repetida.path("updateId").asLong()).isEqualTo(update);
    }

    @Test
    void depoisDeConfirmarPartesoORestoVoltaComOsMesmosIds() throws Exception {
        long update = proximoUpdate();
        String json = callbackNaoVinculado(update);
        JsonNode original = atualizacao(json);
        confirmar(ok("u" + update + "-0"));

        JsonNode repetida = atualizacao(json);

        assertThat(repetida.get("acoes")).hasSize(1);
        assertThat(repetida.get("acoes").get(0)).isEqualTo(original.get("acoes").get(1));
        assertThat(repetida.get("acoes").get(0).path("acaoId").asString())
                .isEqualTo("u" + update + "-1");
    }

    @Test
    void falhaConfirmadaTambemSaiDaListaDeNaoConfirmadas() throws Exception {
        long update = proximoUpdate();
        String json = callbackNaoVinculado(update);
        atualizacao(json);
        confirmar(
                "{\"acaoId\":\"u"
                        + update
                        + "-0\",\"situacao\":\"FALHOU\",\"codigoMotivo\":\"ERRO_TELEGRAM\"}");
        assertThat(atualizacao(json).get("acoes")).hasSize(1);
    }

    @Test
    void duasChamadasParalelasProcessamUmaSoVezEDevolvemAsMesmasAcoes() throws Exception {
        cenario.clara();
        long update = proximoUpdate();
        String json =
                mensagemJson(update, USUARIO_CLARA, "\"text\": \"" + CenarioIT.CODIGO_CLARA + "\"");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<JsonNode>> chamadas = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            chamadas.add(
                    pool.submit(
                            () -> {
                                largada.await();
                                return atualizacao(json);
                            }));
        }
        largada.countDown();
        JsonNode a = chamadas.get(0).get();
        JsonNode b = chamadas.get(1).get();
        pool.shutdown();

        assertThat(a.get("acoes")).isNotEmpty();
        assertThat(b.get("acoes")).isEqualTo(a.get("acoes"));
        // o interpretador rodou uma só vez: um vínculo e uma só linha de update
        assertThat(jdbc.queryForObject("select count(*) from vinculo_telegram", Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from update_telegram_processado where update_id = ?",
                                Integer.class,
                                update))
                .isEqualTo(1);
        assertThat(a.get("acoes").get(0).path("texto").asString())
                .startsWith("Conta vinculada com sucesso, Clara!");
    }

    @Test
    void oEstadoDaConversaJaEstaGravadoQuandoARespostaSaiENadaVaiParaAApiDeBots() throws Exception {
        cenario.clara();

        JsonNode resposta =
                atualizacao(
                        mensagemJson(
                                proximoUpdate(),
                                USUARIO_CLARA,
                                "\"text\": \"" + CenarioIT.CODIGO_CLARA + "\""));

        assertThat(resposta.get("acoes")).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(*) from vinculo_telegram", Integer.class))
                .isEqualTo(1);
        for (String metodo :
                List.of("sendMessage", "editMessageText", "sendDocument", "answerCallbackQuery")) {
            API_BOTS.verify(0, postRequestedFor(urlPathMatching(".*/" + metodo)));
        }
    }

    @Test
    void linhaPerdidaSemConclusaoHaMaisDe30sViraErroInternoComIdEstavel() throws Exception {
        long update = proximoUpdate();
        jdbc.update(
                "insert into update_telegram_processado (update_id, recebido_em, origem) values (?, ?, 'INTERNO')",
                update,
                Timestamp.from(RelogioAjustavel.PADRAO.minus(Duration.ofSeconds(31))));
        String json = mensagemJson(update, USUARIO_CLARA, "\"text\": \"oi\"");

        JsonNode primeira = atualizacao(json);

        assertThat(primeira.get("acoes")).hasSize(1);
        assertThat(primeira.get("acoes").get(0).path("acaoId").asString())
                .isEqualTo("u" + update + "-e0");
        assertThat(primeira.get("acoes").get(0).path("tipo").asString()).isEqualTo("MENSAGEM");
        // repetir devolve a mesma ação, sem gerar outra
        JsonNode segunda = atualizacao(json);
        assertThat(segunda.get("acoes")).isEqualTo(primeira.get("acoes"));
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void linhaEmProcessamentoRecenteDevolveListaVaziaSemInventarErro() throws Exception {
        long update = proximoUpdate();
        jdbc.update(
                "insert into update_telegram_processado (update_id, recebido_em, origem) values (?, ?, 'INTERNO')",
                update,
                Timestamp.from(RelogioAjustavel.PADRAO.minus(Duration.ofSeconds(2))));
        JsonNode resposta = atualizacao(mensagemJson(update, USUARIO_CLARA, "\"text\": \"oi\""));
        assertThat(resposta.get("acoes")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class)).isZero();
    }

    @Test
    void updateDoWebhookAntigoRepetidoNaRotaInternaVoltaVazio() throws Exception {
        long update = proximoUpdate();
        String json = mensagemJson(update, USUARIO_CLARA, "\"text\": \"oi\"");
        mvc.perform(
                        post("/telegram/webhook")
                                .header("X-Telegram-Bot-Api-Secret-Token", SEGREDO)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isOk());

        JsonNode resposta = atualizacao(json);

        assertThat(resposta.get("acoes")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class)).isZero();
    }
}
