package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.ClassificationCases;
import br.com.felipeblaft.reports.support.N8nBackendStub;
import br.com.felipeblaft.reports.support.N8nBotApiStub;
import br.com.felipeblaft.reports.support.N8nTestSupport.Reply;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Falha ao executar uma ação (feature 004, US4, contrato dos workflows §4 e §7: N3 com
 * confirmações, N4 a N10c, N13 e N16): a lista nunca para por falha de execução, cada ação é
 * confirmada logo depois de executada e nenhum texto do erro acompanha a confirmação.
 */
@Tag("n8n")
class N8nFailureHandlingIT {

    private static final String WEBHOOK_SECRET = "secret-webhook-test";
    private static final String DOCUMENT_ID = "d".repeat(43);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static N8nBackendStub backend;
    private static N8nBotApiStub botApi;

    @BeforeAll
    static void startStubs() {
        awaitHealthy(Duration.ofSeconds(180));
        backend = new N8nBackendStub();
        botApi = new N8nBotApiStub();
    }

    @AfterAll
    static void stopStubs() {
        backend.close();
        botApi.close();
    }

    @BeforeEach
    void resetStubs() {
        backend.reset();
        botApi.reset();
    }

    // ---------------------------------------------------------------- apoio

    private static Reply update(String actions) {
        backend.updateReturns(
                "{\"version\":\"v1\",\"updateId\":21,\"batchId\":\"u21\",\"actions\":["
                        + actions
                        + "]}");
        return post(
                N8N_URL + "/webhook/telegram-update",
                Map.of("X-Telegram-Bot-Api-Secret-Token", WEBHOOK_SECRET),
                "application/json",
                "{\"update_id\":21}".getBytes(StandardCharsets.UTF_8));
    }

    private static String message(int number, String text) {
        return "{\"actionId\":\"u21-%d\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\"%s\"}"
                .formatted(number, text);
    }

    private static String edit(int number) {
        return "{\"actionId\":\"u21-%d\",\"type\":\"EDIT\",\"chatId\":7001,\"messageId\":9,\"text\":\"Edited\"}"
                .formatted(number);
    }

    /** O primeiro (e único) resultado de cada confirmação recebida, na ordem. */
    private static List<JsonNode> results() {
        List<JsonNode> results = new ArrayList<>();
        for (LoggedRequest confirmation : backend.confirmations()) {
            JsonNode body = JSON.readTree(confirmation.getBodyAsString());
            assertThat(body.get("version").asString()).isEqualTo("v1");
            assertThat(body.get("results")).hasSize(1);
            results.add(body.get("results").get(0));
        }
        return results;
    }

    private static String reason(JsonNode result) {
        return result.path("reasonCode").asString("");
    }

    // ---------------------------------------------------------------- N4 a N6

    @Test
    @DisplayName("N4: edição recusada vira MESSAGE_NOT_EDITABLE e a terceira ação ainda executa")
    void refusedEditDoesNotStopTheList() {
        botApi.failMethod("editMessageText", 400, "Bad Request: message to edit not found");

        Reply reply = update(message(1, "One") + "," + edit(2) + "," + message(3, "Three"));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(botApi.methods())
                .containsExactly("sendMessage", "editMessageText", "sendMessage");
        List<JsonNode> results = results();
        assertThat(results)
                .extracting(r -> r.get("outcome").asString())
                .containsExactly("EXECUTED", "FAILED", "EXECUTED");
        assertThat(reason(results.get(1))).isEqualTo("MESSAGE_NOT_EDITABLE");
        assertThat(backend.updates()).hasSize(1);
    }

    @Test
    @DisplayName(
            "N5 e N6: cada resposta de erro do Telegram vira o resultado da tabela do contrato")
    void everyTelegramErrorIsClassifiedLikeTheContractTable() {
        for (ClassificationCases.Case scenario : ClassificationCases.all()) {
            backend.reset();
            botApi.reset();
            botApi.failMethod("editMessageText", scenario.status(), scenario.description());

            update(edit(1));

            List<JsonNode> results = results();
            String label = scenario.status() + " " + scenario.description();
            assertThat(results).as(label).hasSize(1);
            JsonNode result = results.getFirst();
            if (scenario.executed()) {
                assertThat(result.get("outcome").asString()).as(label).isEqualTo("EXECUTED");
                assertThat(result.get("messageId").asLong()).as(label).isEqualTo(9L);
            } else {
                assertThat(result.get("outcome").asString()).as(label).isEqualTo("FAILED");
                assertThat(reason(result)).as(label).isEqualTo(scenario.reasonCode());
            }
            if (scenario.description() != null) {
                assertThat(backend.confirmations().getFirst().getBodyAsString())
                        .as("o texto do erro nunca acompanha a confirmação: " + label)
                        .doesNotContain(scenario.description())
                        .doesNotContain("description");
            }
        }
    }

    @Test
    @DisplayName(
            "N6: conexão com a API de bots interrompida ou recusada vira TELEGRAM_ERROR sem texto de erro")
    void connectionFailuresBecomeTelegramError() {
        botApi.breakConnection("sendMessage");
        update(message(1, "One"));
        botApi.close();
        try {
            update(message(2, "Two"));
        } finally {
            botApi = new N8nBotApiStub();
        }

        List<JsonNode> results = results();
        assertThat(results).hasSize(2);
        for (JsonNode result : results) {
            assertThat(result.get("outcome").asString()).isEqualTo("FAILED");
            assertThat(reason(result)).isEqualTo("TELEGRAM_ERROR");
        }
        for (LoggedRequest confirmation : backend.confirmations()) {
            assertThat(confirmation.getBodyAsString())
                    .doesNotContain("ECONN")
                    .doesNotContain("http")
                    .doesNotContain("123456");
        }
    }

    // ---------------------------------------------------------------- N7 e N8

    @Test
    @DisplayName(
            "N7: documento indisponível faz uma só busca, vira DOCUMENT_UNAVAILABLE e não chama sendDocument")
    void unavailableDocumentIsFetchedOnceAndNeverSent() {
        for (int status : new int[] {404, 500}) {
            backend.reset();
            botApi.reset();
            backend.documentStatus(DOCUMENT_ID, status);

            update(
                    "{\"actionId\":\"u21-1\",\"type\":\"DOCUMENT\",\"chatId\":7001,\"documentId\":\""
                            + DOCUMENT_ID
                            + "\",\"fileName\":\"report.pdf\",\"contentType\":\"application/pdf\"},"
                            + message(2, "After"));

            assertThat(backend.documentFetches(DOCUMENT_ID)).as("busca com " + status).hasSize(1);
            assertThat(botApi.methods()).as("com " + status).containsExactly("sendMessage");
            List<JsonNode> results = results();
            assertThat(reason(results.get(0))).isEqualTo("DOCUMENT_UNAVAILABLE");
            assertThat(results.get(0).get("outcome").asString()).isEqualTo("FAILED");
            assertThat(results.get(1).get("outcome").asString()).isEqualTo("EXECUTED");
        }
    }

    @Test
    @DisplayName(
            "N8: tipo novo vira ACTION_NOT_SUPPORTED, as demais executam e campo extra é ignorado")
    void unknownTypeIsReportedAndOthersRun() {
        Reply reply =
                update(
                        message(1, "One")
                                + ",{\"actionId\":\"u21-2\",\"type\":\"NEW_KIND\",\"chatId\":7001,\"extra\":{\"a\":1}},"
                                + "{\"actionId\":\"u21-3\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\"Three\",\"futureField\":true}");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(botApi.methods()).containsExactly("sendMessage", "sendMessage");
        List<JsonNode> results = results();
        assertThat(results).hasSize(3);
        assertThat(results.get(1).get("outcome").asString()).isEqualTo("FAILED");
        assertThat(reason(results.get(1))).isEqualTo("ACTION_NOT_SUPPORTED");
        assertThat(results.get(1).has("messageId")).isFalse();
        assertThat(results.get(2).get("outcome").asString()).isEqualTo("EXECUTED");
    }

    // ---------------------------------------------------------------- N9

    @Test
    @DisplayName("N9: STEP_MESSAGE informa o messageId certo, novo ou editado")
    void stepMessageReportsTheRightMessageId() {
        update(
                "{\"actionId\":\"u21-1\",\"type\":\"STEP_MESSAGE\",\"chatId\":7001,\"text\":\"New\",\"jobId\":\"00000000-0000-4000-8000-000000000001\"},"
                        + "{\"actionId\":\"u21-2\",\"type\":\"STEP_MESSAGE\",\"chatId\":7001,\"text\":\"Edited\",\"jobId\":\"00000000-0000-4000-8000-000000000001\",\"editMessageId\":56}");

        assertThat(botApi.methods()).containsExactly("sendMessage", "editMessageText");
        List<JsonNode> results = results();
        assertThat(results.get(0).get("messageId").asLong()).isEqualTo(4242L);
        assertThat(results.get(1).get("messageId").asLong()).isEqualTo(56L);
    }

    // ---------------------------------------------------------------- N10, N10b, N10c

    @Test
    @DisplayName(
            "N10: só PENDING e DELIVERED executam; as já decididas e a desconhecida são puladas sem confirmar")
    void onlyPendingAndDeliveredAreExecuted() {
        backend.actionState("u21-1", "EXECUTED");
        backend.actionState("u21-2", "FAILED");
        backend.actionState("u21-3", "PERMANENTLY_FAILED");
        backend.actionState("u21-4", "DISCARDED");
        backend.actionStatus("u21-5", 404);
        backend.actionState("u21-6", "PENDING");
        backend.actionState("u21-7", "DELIVERED");

        Reply reply =
                update(
                        String.join(
                                ",",
                                message(1, "S1"),
                                message(2, "S2"),
                                message(3, "S3"),
                                message(4, "S4"),
                                message(5, "S5"),
                                message(6, "Run6"),
                                message(7, "Run7")));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(botApi.callsOf("sendMessage"))
                .extracting(call -> JSON.readTree(call.getBodyAsString()).get("text").asString())
                .containsExactly("Run6", "Run7");
        assertThat(results())
                .extracting(r -> r.get("actionId").asString())
                .containsExactly("u21-6", "u21-7");
        assertThat(backend.stateQueries()).hasSize(7);
    }

    // ---------------------------------------------------------------- continuidade e ordem

    @Test
    @DisplayName("o toque é executado mesmo depois de uma falha anterior")
    void callbackReplyRunsAfterAnEarlierFailure() {
        botApi.failMethod("sendMessage", 403, "Forbidden: bot was blocked by the user");

        update(
                message(1, "One")
                        + ",{\"actionId\":\"u21-2\",\"type\":\"CALLBACK_REPLY\",\"callbackId\":\"cb-1\"}");

        assertThat(botApi.methods()).containsExactly("sendMessage", "answerCallbackQuery");
        List<JsonNode> results = results();
        assertThat(reason(results.get(0))).isEqualTo("CHAT_UNAVAILABLE");
        assertThat(results.get(1).get("outcome").asString()).isEqualTo("EXECUTED");
    }

    @Test
    @DisplayName("cada ação é confirmada logo depois de executada, antes da seguinte")
    void confirmationComesRightAfterEachAction() {
        update(message(1, "One") + "," + message(2, "Two") + "," + message(3, "Three"));

        record Event(java.util.Date moment, String kind) {}
        List<Event> events = new ArrayList<>();
        botApi.calls().forEach(call -> events.add(new Event(call.getLoggedDate(), "bot")));
        backend.confirmations()
                .forEach(call -> events.add(new Event(call.getLoggedDate(), "confirmation")));
        events.sort(Comparator.comparing(Event::moment));

        assertThat(events)
                .extracting(Event::kind)
                .containsExactly(
                        "bot", "confirmation", "bot", "confirmation", "bot", "confirmation");
    }

    @Test
    @DisplayName(
            "N13: falha de transporte na consulta de estado aborta a lista com 503 e o que foi feito fica confirmado")
    void transportFailureAbortsTheListAfterConfirmingWhatWasDone() {
        backend.actionStatus("u21-2", 500);

        Reply reply =
                update(message(1, "One") + "," + message(2, "Two") + "," + message(3, "Three"));

        assertThat(reply.status()).isEqualTo(503);
        assertThat(reply.body()).isEmpty();
        assertThat(botApi.methods()).containsExactly("sendMessage");
        assertThat(results())
                .extracting(r -> r.get("actionId").asString())
                .containsExactly("u21-1");
    }

    @Test
    @DisplayName("N13: consulta de estado recusada com 401 também aborta a lista")
    void unauthorizedStateQueryAbortsTheList() {
        backend.actionStatus("u21-1", 401);

        Reply reply = update(message(1, "One") + "," + message(2, "Two"));

        assertThat(reply.status()).isEqualTo(503);
        assertThat(botApi.calls()).isEmpty();
    }

    // ---------------------------------------------------------------- N16

    @Test
    @DisplayName(
            "N16: confirmação recusada três vezes não derruba a lista nem a resposta, e nada é reexecutado")
    void refusedConfirmationDoesNotBreakTheList() {
        backend.confirmationsStatus(500);

        Reply reply = update(message(1, "One") + "," + message(2, "Two"));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(botApi.methods()).containsExactly("sendMessage", "sendMessage");
        assertThat(backend.confirmations()).hasSize(6);
    }
}
