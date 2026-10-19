package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.get;
import static br.com.felipeblaft.reports.support.N8nTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.N8nBackendStub;
import br.com.felipeblaft.reports.support.N8nBotApiStub;
import br.com.felipeblaft.reports.support.N8nTestSupport.Reply;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Segurança do transporte (feature 004, US5, FR-009 e FR-010, contrato dos workflows W2): a
 * autorização é do backend; o n8n só repassa o segredo do webhook e o token de serviço, não valida
 * o segredo do Telegram por conta própria e nunca age quando o backend recusa. A convivência dos
 * dois tokens de serviço no backend real é provada pelo ensaio de rotação ({@code tests
 * rehearsal}).
 */
@Tag("n8n")
class N8nSecurityIT {

    private static final String WEBHOOK_SECRET = "secret-webhook-test";
    private static final String UPDATE_WITH_ONE_MESSAGE =
            "{\"version\":\"v1\",\"updateId\":61,\"batchId\":\"u61\",\"actions\":[{\"actionId\":\"u61-1\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\"Plain body\"}]}";

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

    private static Reply update(String secretHeader) {
        Map<String, String> headers = new HashMap<>();
        if (secretHeader != null) {
            headers.put("X-Telegram-Bot-Api-Secret-Token", secretHeader);
        }
        return post(
                N8N_URL + "/webhook/telegram-update",
                headers,
                "application/json",
                "{\"update_id\":61}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName(
            "segredo do webhook ausente ou errado: o backend recusa com 401, o n8n responde 503 e não envia nada")
    void missingOrWrongWebhookSecretSendsNothing() {
        backend.requireCredentials(
                WEBHOOK_SECRET, N8nBackendStub.SERVICE_TOKEN, UPDATE_WITH_ONE_MESSAGE);

        for (String secret : new String[] {null, "", "wrong-secret"}) {
            Reply reply = update(secret);

            assertThat(reply.status()).as("segredo '" + secret + "'").isEqualTo(503);
            assertThat(reply.body()).isEmpty();
        }
        assertThat(botApi.calls()).isEmpty();
        assertThat(backend.confirmations()).isEmpty();
    }

    @Test
    @DisplayName("segredo e token certos: a mensagem é enviada")
    void correctCredentialsDeliverTheMessage() {
        backend.requireCredentials(
                WEBHOOK_SECRET, N8nBackendStub.SERVICE_TOKEN, UPDATE_WITH_ONE_MESSAGE);

        Reply reply = update(WEBHOOK_SECRET);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(botApi.sentTexts()).containsExactly("Plain body");
    }

    @Test
    @DisplayName("o n8n não valida o segredo do Telegram por conta própria: repassa o que recebeu")
    void theOrchestratorDoesNotValidateTheTelegramSecret() {
        Reply reply = update("any-value-the-backend-would-judge");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(backend.updates()).hasSize(1);
        assertThat(backend.updates().getFirst().getHeader("X-Telegram-Bot-Api-Secret-Token"))
                .isEqualTo("any-value-the-backend-would-judge");
    }

    @Test
    @DisplayName(
            "token de serviço do n8n recusado pelo backend: deep mostra unauthorized e o update responde 503")
    void rejectedServiceTokenIsVisibleAndNothingIsSent() {
        backend.requireServiceToken("a-token-the-orchestrator-does-not-have");

        Reply deep = get(N8N_URL + "/webhook/transport-health/deep");
        Reply reply = update(WEBHOOK_SECRET);

        assertThat(deep.status()).isEqualTo(503);
        assertThat(deep.body()).contains("\"backend\":\"unauthorized\"");
        assertThat(reply.status()).isEqualTo(503);
        assertThat(botApi.sentTexts()).isEmpty();
        assertThat(botApi.methods())
                .doesNotContain("sendMessage", "sendDocument", "editMessageText");
    }

    @Test
    @DisplayName("nenhuma chamada a getFile: o áudio nunca passa pelo n8n")
    void audioNeverGoesThroughTheOrchestrator() {
        post(
                N8N_URL + "/webhook/telegram-update",
                Map.of("X-Telegram-Bot-Api-Secret-Token", WEBHOOK_SECRET),
                "application/json",
                "{\"update_id\":62,\"message\":{\"voice\":{\"file_id\":\"file-x\",\"file_unique_id\":\"u-x\",\"duration\":3}}}"
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(botApi.methods()).doesNotContain("getFile");
        assertThat(botApi.calls()).isEmpty();
    }
}
