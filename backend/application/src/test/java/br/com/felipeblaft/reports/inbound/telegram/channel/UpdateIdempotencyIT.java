package br.com.felipeblaft.reports.inbound.telegram.channel;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.support.ActionsSchemaV1;
import br.com.felipeblaft.reports.support.AdjustableClock;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
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
class UpdateIdempotencyIT extends TelegramConversationBase {

    private static final String BASE = "/interno/v1/canal/telegram";

    private JsonNode update(String json) throws Exception {
        String body =
                mvc.perform(
                                post(BASE + "/atualizacao")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                        .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        ActionsSchemaV1.require("RespostaAtualizacao", body);
        return JSON.readTree(body);
    }

    private void confirm(String resultsBody) throws Exception {
        mvc.perform(
                        post(BASE + "/confirmacoes")
                                .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"versao\":\"v1\",\"resultados\":[" + resultsBody + "]}"))
                .andExpect(status().isOk());
    }

    private static String ok(String id) {
        return "{\"acaoId\":\"" + id + "\",\"situacao\":\"EXECUTADA\"}";
    }

    /**
     * Toque de botão de professor não vinculado: RESPOSTA_CALLBACK e a mensagem de código
     * necessário.
     */
    private String unlinkedCallback(long update) {
        return """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": "v1:reg:",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                .formatted(update, update, USER_CLARA, USER_CLARA);
    }

    @Test
    void sameUpdateWithoutConfirmingReturnsSameActionsWithSameIds() throws Exception {
        long update = nextUpdate();
        String json = unlinkedCallback(update);

        JsonNode first = update(json);
        JsonNode second = update(json);
        JsonNode third = update(json);

        assertThat(first.get("acoes")).hasSize(2);
        assertThat(second.get("acoes")).isEqualTo(first.get("acoes"));
        assertThat(third.get("acoes")).isEqualTo(first.get("acoes"));
        assertThat(first.get("acoes").get(0).path("tipo").asString())
                .isEqualTo("RESPOSTA_CALLBACK");
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select total_actions from processed_telegram_update where update_id = ?",
                                Integer.class,
                                update))
                .isEqualTo(2);
    }

    @Test
    void afterConfirmingEverythingRepeatedUpdateReturnsEmpty() throws Exception {
        long update = nextUpdate();
        String json = unlinkedCallback(update);
        update(json);
        confirm(ok("u" + update + "-0") + "," + ok("u" + update + "-1"));

        JsonNode repeated = update(json);

        assertThat(repeated.get("acoes")).isEmpty();
        assertThat(repeated.path("updateId").asLong()).isEqualTo(update);
    }

    @Test
    void afterConfirmingPartTheRestReturnsWithSameIds() throws Exception {
        long update = nextUpdate();
        String json = unlinkedCallback(update);
        JsonNode original = update(json);
        confirm(ok("u" + update + "-0"));

        JsonNode repeated = update(json);

        assertThat(repeated.get("acoes")).hasSize(1);
        assertThat(repeated.get("acoes").get(0)).isEqualTo(original.get("acoes").get(1));
        assertThat(repeated.get("acoes").get(0).path("acaoId").asString())
                .isEqualTo("u" + update + "-1");
    }

    @Test
    void confirmedFailureAlsoLeavesUnconfirmedList() throws Exception {
        long update = nextUpdate();
        String json = unlinkedCallback(update);
        update(json);
        confirm(
                "{\"acaoId\":\"u"
                        + update
                        + "-0\",\"situacao\":\"FALHOU\",\"codigoMotivo\":\"ERRO_TELEGRAM\"}");
        assertThat(update(json).get("acoes")).hasSize(1);
    }

    @Test
    void twoParallelCallsProcessOnlyOnceAndReturnSameActions() throws Exception {
        scenario.clara();
        long update = nextUpdate();
        String json =
                messageJson(update, USER_CLARA, "\"text\": \"" + ScenarioIT.CODE_CLARA + "\"");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JsonNode>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            calls.add(
                    pool.submit(
                            () -> {
                                start.await();
                                return update(json);
                            }));
        }
        start.countDown();
        JsonNode a = calls.get(0).get();
        JsonNode b = calls.get(1).get();
        pool.shutdown();

        assertThat(a.get("acoes")).isNotEmpty();
        assertThat(b.get("acoes")).isEqualTo(a.get("acoes"));
        // o interpretador rodou uma só vez: um vínculo e uma só linha de update
        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from processed_telegram_update where update_id = ?",
                                Integer.class,
                                update))
                .isEqualTo(1);
        assertThat(a.get("acoes").get(0).path("texto").asString())
                .startsWith("Conta vinculada com sucesso, Clara!");
    }

    @Test
    void conversationStateIsAlreadySavedWhenReplyGoesOutAndNothingGoesToBotApi() throws Exception {
        scenario.clara();

        JsonNode response =
                update(
                        messageJson(
                                nextUpdate(),
                                USER_CLARA,
                                "\"text\": \"" + ScenarioIT.CODE_CLARA + "\""));

        assertThat(response.get("acoes")).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isEqualTo(1);
        for (String method :
                List.of("sendMessage", "editMessageText", "sendDocument", "answerCallbackQuery")) {
            API_BOTS.verify(0, postRequestedFor(urlPathMatching(".*/" + method)));
        }
    }

    @Test
    void lostRowWithoutConclusionForMoreThan30sBecomesInternalErrorWithStableId() throws Exception {
        long update = nextUpdate();
        jdbc.update(
                "insert into processed_telegram_update (update_id, received_at, origin) values (?, ?, 'INTERNO')",
                update,
                Timestamp.from(AdjustableClock.DEFAULT.minus(Duration.ofSeconds(31))));
        String json = messageJson(update, USER_CLARA, "\"text\": \"oi\"");

        JsonNode first = update(json);

        assertThat(first.get("acoes")).hasSize(1);
        assertThat(first.get("acoes").get(0).path("acaoId").asString())
                .isEqualTo("u" + update + "-e0");
        assertThat(first.get("acoes").get(0).path("tipo").asString()).isEqualTo("MENSAGEM");
        // repetir devolve a mesma ação, sem gerar outra
        JsonNode second = update(json);
        assertThat(second.get("acoes")).isEqualTo(first.get("acoes"));
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void recentRowInProcessingReturnsEmptyListWithoutInventingError() throws Exception {
        long update = nextUpdate();
        jdbc.update(
                "insert into processed_telegram_update (update_id, received_at, origin) values (?, ?, 'INTERNO')",
                update,
                Timestamp.from(AdjustableClock.DEFAULT.minus(Duration.ofSeconds(2))));
        JsonNode response = update(messageJson(update, USER_CLARA, "\"text\": \"oi\""));
        assertThat(response.get("acoes")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isZero();
    }

    @Test
    void repeatedOldWebhookUpdateOnInternalRouteReturnsEmpty() throws Exception {
        long update = nextUpdate();
        String json = messageJson(update, USER_CLARA, "\"text\": \"oi\"");
        mvc.perform(
                        post("/telegram/webhook")
                                .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isOk());

        JsonNode response = update(json);

        assertThat(response.get("acoes")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isZero();
    }
}
