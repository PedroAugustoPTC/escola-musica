package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.N8nBackendStub;
import br.com.felipeblaft.reports.support.N8nBotApiStub;
import br.com.felipeblaft.reports.support.N8nTestSupport.Reply;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
 * Ação {@code DELETE_MESSAGE} pelo n8n real (feature 005, FR-036, contrato dos workflows §4): o
 * workflow só traduz para {@code deleteMessage}, classifica a falha como rótulo e confirma. Quem
 * decide a compensação é o backend; o n8n não cria ação, não monta texto e não valida.
 */
@Tag("n8n")
class N8nDeleteMessageIT {

    private static final String WEBHOOK_SECRET = "secret-webhook-test";
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

    private static Reply update(String actions) {
        backend.updateReturns(
                "{\"version\":\"v1\",\"updateId\":31,\"batchId\":\"u31\",\"actions\":["
                        + actions
                        + "]}");
        return post(
                N8N_URL + "/webhook/telegram-update",
                Map.of("X-Telegram-Bot-Api-Secret-Token", WEBHOOK_SECRET),
                "application/json",
                "{\"update_id\":31}".getBytes(StandardCharsets.UTF_8));
    }

    private static String delete(int number) {
        return "{\"actionId\":\"u31-%d\",\"type\":\"DELETE_MESSAGE\",\"chatId\":7001,\"messageId\":89}"
                .formatted(number);
    }

    private static String message(int number, String text) {
        return "{\"actionId\":\"u31-%d\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\"%s\"}"
                .formatted(number, text);
    }

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

    @Test
    @DisplayName("D1: DELETE_MESSAGE chama deleteMessage com chat_id e message_id e confirma EXECUTED")
    void deletionCallsDeleteMessageAndConfirmsExecuted() {
        Reply reply = update(message(1, "One") + "," + delete(2) + "," + message(3, "Three"));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(botApi.methods())
                .containsExactly("sendMessage", "deleteMessage", "sendMessage");
        JsonNode body = JSON.readTree(botApi.callsOf("deleteMessage").getFirst().getBodyAsString());
        assertThat(body.get("chat_id").asLong()).isEqualTo(7001L);
        assertThat(body.get("message_id").asLong()).isEqualTo(89L);
        assertThat(body.size()).isEqualTo(2);
        List<JsonNode> results = results();
        assertThat(results)
                .extracting(r -> r.get("outcome").asString())
                .containsExactly("EXECUTED", "EXECUTED", "EXECUTED");
        assertThat(results.get(1).get("actionId").asString()).isEqualTo("u31-2");
        assertThat(results.get(1).has("reasonCode")).isFalse();
        assertThat(results.get(1).has("messageId")).as("apagar não cria nem edita mensagem").isFalse();
        assertThat(botApi.sentTexts()).containsExactly("One", "Three");
    }

    @Test
    @DisplayName("D2: mensagem que o Telegram não apaga vira MESSAGE_NOT_DELETABLE e a lista segue")
    void refusedDeletionBecomesMessageNotDeletableAndTheListContinues() {
        for (String description :
                List.of(
                        "Bad Request: message to delete not found",
                        "Bad Request: message can't be deleted",
                        "Bad Request: message can't be deleted for everyone",
                        "Bad Request: MESSAGE_ID_INVALID")) {
            backend.reset();
            botApi.reset();
            botApi.failMethod("deleteMessage", 400, description);

            Reply reply = update(delete(1) + "," + message(2, "After"));

            assertThat(reply.status()).as(description).isEqualTo(200);
            List<JsonNode> results = results();
            assertThat(results)
                    .as(description)
                    .extracting(r -> r.get("outcome").asString())
                    .containsExactly("FAILED", "EXECUTED");
            assertThat(reason(results.get(0))).as(description).isEqualTo("MESSAGE_NOT_DELETABLE");
            assertThat(botApi.methods())
                    .as(description)
                    .containsExactly("deleteMessage", "sendMessage");
            assertThat(backend.confirmations().getFirst().getBodyAsString())
                    .as("o texto do erro nunca acompanha a confirmação")
                    .doesNotContain("not found")
                    .doesNotContain("description");
        }
    }

    @Test
    @DisplayName("D3: as demais falhas de deleteMessage seguem a tabela geral de classificação")
    void otherDeletionFailuresFollowTheGeneralTable() {
        Map<Integer, String> expected =
                Map.of(403, "CHAT_UNAVAILABLE", 429, "RATE_LIMITED", 500, "TELEGRAM_ERROR");
        for (Map.Entry<Integer, String> scenario : expected.entrySet()) {
            backend.reset();
            botApi.reset();
            botApi.failMethod("deleteMessage", scenario.getKey(), "Some failure");

            update(delete(1));

            assertThat(reason(results().getFirst()))
                    .as("status " + scenario.getKey())
                    .isEqualTo(scenario.getValue());
        }
    }

    @Test
    @DisplayName("D4: o n8n não cria ação nem compensa: só confirma, uma vez, a ação recebida")
    void theOrchestratorNeverCreatesTheCompensation() {
        botApi.failMethod("deleteMessage", 400, "Bad Request: message can't be deleted");

        update(delete(1));

        assertThat(backend.confirmations()).hasSize(1);
        assertThat(botApi.methods()).containsExactly("deleteMessage");
        assertThat(botApi.sentTexts()).isEmpty();
        assertThat(backend.updates()).hasSize(1);
    }

    @Test
    @DisplayName("D5: ação DELETE_MESSAGE já tratada pelo backend é pulada, sem chamada nem confirmação")
    void alreadyHandledDeletionIsSkipped() {
        backend.actionState("u31-1", "EXECUTED");

        update(delete(1) + "," + message(2, "After"));

        assertThat(botApi.methods()).containsExactly("sendMessage");
        assertThat(backend.confirmations()).hasSize(1);
    }
}
