package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.N8nBackendStub;
import br.com.felipeblaft.reports.support.N8nBotApiStub;
import br.com.felipeblaft.reports.support.N8nTestSupport.Reply;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato do workflow {@code channel-delivery} pelo n8n real (feature 004, contrato dos workflows
 * §3 e §7: N11 e N12): o token de entrega é conferido antes de qualquer coisa, e o lote só é aceito
 * (200) depois de executado e confirmado.
 */
@Tag("n8n")
class N8nDeliveryWorkflowIT {

    static final String DELIVERY_TOKEN = "orchestrator-secret";
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

    static String batch(String batchId, String... texts) {
        StringBuilder actions = new StringBuilder();
        for (int i = 0; i < texts.length; i++) {
            if (i > 0) {
                actions.append(',');
            }
            actions.append(
                    "{\"actionId\":\"%s-%d\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\"%s\"}"
                            .formatted(batchId, i + 1, texts[i]));
        }
        return ("{\"version\":\"v1\",\"batchId\":\"%s\",\"origin\":\"PROACTIVE\","
                        + "\"createdAt\":\"2026-10-16T12:00:00Z\",\"attempt\":1,\"actions\":[%s]}")
                .formatted(batchId, actions);
    }

    static Reply deliver(String token, String idempotencyKey, String body) {
        Map<String, String> headers = new HashMap<>();
        if (token != null) {
            headers.put("X-Channel-Delivery-Token", token);
        }
        headers.put("Idempotency-Key", idempotencyKey);
        return post(
                N8N_URL + "/webhook/channel-delivery",
                headers,
                "application/json",
                body.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- N11

    @Test
    @DisplayName("N11: sem token e com token errado responde 401 sem corpo e não chama ninguém")
    void missingOrWrongTokenIsRefusedWithoutAnyCall() {
        for (String token : new String[] {null, "", "wrong-token"}) {
            Reply reply = deliver(token, "u41", batch("u41", "One"));

            assertThat(reply.status()).as("token '" + token + "'").isEqualTo(401);
            assertThat(reply.body()).isEmpty();
        }
        assertThat(backend.all()).isEmpty();
        assertThat(botApi.calls()).isEmpty();
    }

    // ---------------------------------------------------------------- N12

    @Test
    @DisplayName("N12: lote válido é aceito só depois de executado e confirmado, na ordem")
    void validBatchIsAcceptedOnlyAfterExecutionAndConfirmation() {
        botApi.delayWhenBodyContains("sendMessage", "Two", 1500);

        long before = System.nanoTime();
        Reply reply = deliver(DELIVERY_TOKEN, "u42", batch("u42", "One", "Two", "Three"));
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body()).isEmpty();
        assertThat(elapsedMs).as("a resposta espera a execução lenta").isGreaterThanOrEqualTo(1500);
        assertThat(botApi.sentTexts()).containsExactly("One", "Two", "Three");
        assertThat(backend.confirmations()).hasSize(3);
        assertThat(backend.headerViolations()).isEmpty();
    }

    @Test
    @DisplayName("lote repetido com os mesmos actionId não reexecuta o que já foi tratado")
    void repeatedBatchDoesNotRunAgain() {
        deliver(DELIVERY_TOKEN, "u43", batch("u43", "One", "Two"));
        backend.actionState("u43-1", "EXECUTED");
        backend.actionState("u43-2", "EXECUTED");

        Reply again = deliver(DELIVERY_TOKEN, "u43", batch("u43", "One", "Two"));

        assertThat(again.status()).isEqualTo(200);
        assertThat(botApi.sentTexts()).containsExactly("One", "Two");
        assertThat(backend.confirmations()).hasSize(2);
    }

    @Test
    @DisplayName(
            "falha de transporte com o backend durante a execução responde 503 para o backend reentregar")
    void backendTransportFailureAnswers503() {
        backend.actionStatus("u44-2", 500);

        Reply reply = deliver(DELIVERY_TOKEN, "u44", batch("u44", "One", "Two", "Three"));

        assertThat(reply.status()).isEqualTo(503);
        assertThat(reply.body()).isEmpty();
        assertThat(botApi.sentTexts()).containsExactly("One");
    }

    @Test
    @DisplayName(
            "o n8n não guarda o Idempotency-Key nem o corpo: nada do lote fica em execução salva")
    void batchIsNotKeptByTheOrchestrator() {
        deliver(DELIVERY_TOKEN, "u45-unique-key", batch("u45", "One"));

        List<String> keys =
                backend.all().stream()
                        .map(request -> request.getHeader("Idempotency-Key"))
                        .toList();
        assertThat(keys).allMatch(java.util.Objects::isNull);
    }
}
