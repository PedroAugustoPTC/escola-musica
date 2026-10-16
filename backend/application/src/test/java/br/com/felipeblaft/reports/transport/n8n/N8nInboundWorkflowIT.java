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
 * Contrato do workflow {@code telegram-inbound} pelo n8n real (feature 004, contrato dos workflows
 * §2 e §7: N1, N2, N3 sem confirmação, N15 e N17). O backend e a API de bots são simulados pela JVM
 * do teste.
 */
@Tag("n8n")
class N8nInboundWorkflowIT {

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
        if (backend != null) {
            backend.close();
        }
        botApi.close();
    }

    @BeforeEach
    void resetStubs() {
        backend.reset();
        botApi.reset();
    }

    /** Envia um update ao webhook do n8n como o servidor local da API de bots faria. */
    private static Reply sendUpdate(byte[] body) {
        return post(
                N8N_URL + "/webhook/telegram-update",
                Map.of("X-Telegram-Bot-Api-Secret-Token", WEBHOOK_SECRET),
                "application/json",
                body);
    }

    private static byte[] textUpdate(String text) {
        return ("{\"update_id\": 11,\n \"message\":{\"message_id\":5,\"from\":{\"id\":7001},"
                        + "\"chat\":{\"id\":7001,\"type\":\"private\"},\"text\":\""
                        + text
                        + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String actionsResponse(String actions) {
        return "{\"version\":\"v1\",\"updateId\":11,\"batchId\":\"u11\",\"actions\":["
                + actions
                + "]}";
    }

    // ---------------------------------------------------------------- N1

    @Test
    @DisplayName("N1: o corpo segue idêntico ao backend com os dois cabeçalhos")
    void forwardsBodyAndHeadersUnchanged() {
        byte[] body = textUpdate("olá  mundo");

        Reply reply = sendUpdate(body);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body()).isEmpty();
        List<LoggedRequest> updates = backend.updates();
        assertThat(updates).hasSize(1);
        assertThat(updates.getFirst().getBody()).isEqualTo(body);
        assertThat(updates.getFirst().getHeader("X-N8N-Service-Token"))
                .isEqualTo(N8nBackendStub.SERVICE_TOKEN);
        assertThat(updates.getFirst().getHeader("X-Telegram-Bot-Api-Secret-Token"))
                .isEqualTo(WEBHOOK_SECRET);
        assertThat(updates.getFirst().getHeader("Content-Type")).startsWith("application/json");
        assertThat(backend.headerViolations()).isEmpty();
    }

    // ---------------------------------------------------------------- N2

    @Test
    @DisplayName("N2: backend com 401 ou 500 responde 503 sem corpo e nada é enviado")
    void backendFailureAnswers503() {
        for (int status : new int[] {401, 500}) {
            backend.reset();
            botApi.reset();
            backend.updateStatus(status);

            Reply reply = sendUpdate(textUpdate("oi"));

            assertThat(reply.status()).as("status do backend " + status).isEqualTo(503);
            assertThat(reply.body()).isEmpty();
            assertThat(botApi.calls()).isEmpty();
            assertThat(backend.confirmations()).isEmpty();
        }
    }

    @Test
    @DisplayName("N2: backend fora do ar responde 503 sem corpo e nada é enviado")
    void backendDownAnswers503() {
        backend.close();
        try {
            Reply reply = sendUpdate(textUpdate("oi"));

            assertThat(reply.status()).isEqualTo(503);
            assertThat(reply.body()).isEmpty();
            assertThat(botApi.calls()).isEmpty();
        } finally {
            backend = new N8nBackendStub();
        }
    }

    // ---------------------------------------------------------------- N3 (sem confirmação)

    @Test
    @DisplayName("N3: toque, mensagem e documento são executados nessa ordem, com um só GET do PDF")
    void executesCallbackMessageAndDocumentInOrder() throws Exception {
        byte[] pdf = ("%PDF-1.4\n" + "x".repeat(2000)).getBytes(StandardCharsets.ISO_8859_1);
        backend.documentReturns(DOCUMENT_ID, pdf);
        backend.updateReturns(
                actionsResponse(
                        """
                        {"actionId":"u11-1","type":"CALLBACK_REPLY","callbackId":"cb-1"},
                        {"actionId":"u11-2","type":"MESSAGE","chatId":7001,"text":"Olá <b>mundo</b>",
                         "keyboard":{"rows":[[{"label":"Sim","data":"v1:a:b"},{"label":"Não","data":"v1:c:d"}]]}},
                        {"actionId":"u11-3","type":"DOCUMENT","chatId":7001,"documentId":"%s",
                         "fileName":"report.pdf","contentType":"application/pdf","caption":"Legenda"}
                        """
                                .formatted(DOCUMENT_ID)));

        Reply reply = sendUpdate(textUpdate("pronto"));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(botApi.methods())
                .containsExactly("answerCallbackQuery", "sendMessage", "sendDocument");
        JsonNode callback =
                JSON.readTree(botApi.callsOf("answerCallbackQuery").getFirst().getBodyAsString());
        assertThat(callback.get("callback_query_id").asString()).isEqualTo("cb-1");
        JsonNode message =
                JSON.readTree(botApi.callsOf("sendMessage").getFirst().getBodyAsString());
        assertThat(message.get("chat_id").asLong()).isEqualTo(7001L);
        assertThat(message.get("text").asString()).isEqualTo("Olá <b>mundo</b>");
        assertThat(message.get("parse_mode").asString()).isEqualTo("HTML");
        assertThat(message.path("link_preview_options").path("is_disabled").asBoolean()).isTrue();
        JsonNode buttons = message.path("reply_markup").path("inline_keyboard");
        assertThat(buttons).hasSize(1);
        assertThat(buttons.get(0).get(0).get("text").asString()).isEqualTo("Sim");
        assertThat(buttons.get(0).get(0).get("callback_data").asString()).isEqualTo("v1:a:b");
        assertThat(buttons.get(0).get(1).get("callback_data").asString()).isEqualTo("v1:c:d");
        assertThat(backend.documentFetches(DOCUMENT_ID)).hasSize(1);
        LoggedRequest document = botApi.callsOf("sendDocument").getFirst();
        assertThat(document.getHeader("Content-Type")).startsWith("multipart/form-data");
        assertThat(document.getBodyAsString()).contains("report.pdf").contains("%PDF-1.4");
        List<LoggedRequest> confirmations = backend.confirmations();
        assertThat(confirmations).hasSize(3);
        for (int i = 0; i < 3; i++) {
            JsonNode result =
                    JSON.readTree(confirmations.get(i).getBodyAsString()).get("results").get(0);
            assertThat(result.get("actionId").asString()).isEqualTo("u11-" + (i + 1));
            assertThat(result.get("outcome").asString()).isEqualTo("EXECUTED");
        }
        assertThat(backend.headerViolations()).isEmpty();
    }

    @Test
    @DisplayName("N3: MESSAGE sem teclado não leva reply_markup")
    void messageWithoutKeyboardHasNoReplyMarkup() throws Exception {
        backend.updateReturns(
                actionsResponse(
                        "{\"actionId\":\"u11-1\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\"Plain body\"}"));

        sendUpdate(textUpdate("oi"));

        JsonNode message =
                JSON.readTree(botApi.callsOf("sendMessage").getFirst().getBodyAsString());
        assertThat(message.has("reply_markup")).isFalse();
    }

    @Test
    @DisplayName(
            "N3: EDIT e STEP_MESSAGE usam editMessageText, e STEP_MESSAGE sem editMessageId envia mensagem nova")
    void editAndStepMessages() throws Exception {
        backend.updateReturns(
                actionsResponse(
                        """
                        {"actionId":"u11-1","type":"EDIT","chatId":7001,"messageId":55,"text":"Editado"},
                        {"actionId":"u11-2","type":"STEP_MESSAGE","chatId":7001,"jobId":"00000000-0000-4000-8000-000000000001","text":"Etapa","editMessageId":56},
                        {"actionId":"u11-3","type":"STEP_MESSAGE","chatId":7001,"jobId":"00000000-0000-4000-8000-000000000001","text":"Nova"}
                        """));

        sendUpdate(textUpdate("oi"));

        assertThat(botApi.methods())
                .containsExactly("editMessageText", "editMessageText", "sendMessage");
        JsonNode edit = JSON.readTree(botApi.callsOf("editMessageText").get(0).getBodyAsString());
        assertThat(edit.get("message_id").asLong()).isEqualTo(55L);
        assertThat(edit.path("reply_markup").path("inline_keyboard")).isEmpty();
        JsonNode step = JSON.readTree(botApi.callsOf("editMessageText").get(1).getBodyAsString());
        assertThat(step.get("message_id").asLong()).isEqualTo(56L);
        assertThat(step.get("text").asString()).isEqualTo("Etapa");
    }

    // ---------------------------------------------------------------- N15 e N17

    @Test
    @DisplayName("N15: áudio segue ao backend com o file_id e o n8n nunca chama getFile")
    void audioIsForwardedWithoutDownload() {
        byte[] body =
                ("{\"update_id\":12,\"message\":{\"message_id\":6,\"from\":{\"id\":7001},"
                                + "\"chat\":{\"id\":7001,\"type\":\"private\"},"
                                + "\"voice\":{\"file_id\":\"file-abc\",\"file_unique_id\":\"u-abc\",\"duration\":30}}}")
                        .getBytes(StandardCharsets.UTF_8);

        Reply reply = sendUpdate(body);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(backend.updates().getFirst().getBodyAsString()).contains("file-abc");
        assertThat(botApi.methods()).doesNotContain("getFile");
        assertThat(botApi.calls()).isEmpty();
    }

    @Test
    @DisplayName(
            "N17: lista vazia (grupo, tipo não suportado ou formato inesperado) não envia nada")
    void emptyActionListSendsNothing() {
        byte[] group =
                ("{\"update_id\":13,\"message\":{\"message_id\":7,\"from\":{\"id\":7001},"
                                + "\"chat\":{\"id\":-1001,\"type\":\"group\"},\"text\":\"oi\"}}")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] unexpectedShape =
                "{\"update_id\":14,\"poll\":{\"id\":\"1\"}}".getBytes(StandardCharsets.UTF_8);

        for (byte[] body : List.of(group, unexpectedShape)) {
            Reply reply = sendUpdate(body);

            assertThat(reply.status()).isEqualTo(200);
            assertThat(reply.body()).isEmpty();
        }
        assertThat(botApi.calls()).isEmpty();
        assertThat(backend.confirmations()).isEmpty();
    }
}
