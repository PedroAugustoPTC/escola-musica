package br.com.felipeblaft.reports.inbound.telegram.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.inbound.telegram.TelegramConversationInterpreter;
import br.com.felipeblaft.reports.support.ActionsSchemaV1;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ChannelOpenApiContract;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * V2, C1 a C3 e C13 a C14: toda resposta da API interna segue o formato v1 normativo ({@code
 * acoes-v1.schema.json}) e as rotas e códigos HTTP seguem o OpenAPI. O estado da conversa é montado
 * pelo webhook; o que se verifica aqui é a nova porta.
 */
class TelegramChannelContractIT extends TelegramConversationBase {

    private static final String BASE = "/internal/v1/channel/telegram";
    private static final String INTERNAL_ERROR_TEXT =
            "Ocorreu um erro inesperado. Tente novamente mais tarde.";

    @MockitoSpyBean TelegramConversationInterpreter interpreter;

    @MockitoSpyBean ChannelActionMapper mapper;

    private MvcResult update(String json) throws Exception {
        MvcResult result =
                mvc.perform(
                                post(BASE + "/update")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                        .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        ActionsSchemaV1.require("UpdateResponse", result.getResponse().getContentAsString());
        ChannelOpenApiContract.requireReply(result);
        return result;
    }

    private JsonNode actions(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString()).get("actions");
    }

    private MvcResult callback(long user, String data) throws Exception {
        return update(
                """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": %s,
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(
                                nextUpdate(),
                                nextUpdate(),
                                JSON.writeValueAsString(data),
                                user,
                                user));
    }

    private UUID prepareReview() throws Exception {
        Teacher clara = scenario.clara();
        scenario.student(clara, "Matteo Ferreira", "Piano");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        prepareTelegramFile("lesson", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        sendText(USER_CLARA, "Matteo");
        sendVoice(USER_CLARA, "lesson", 1000);
        sendCallback(USER_CLARA, "v1:confirm-student:");
        scenario.processQueue();
        sendText(USER_CLARA, "Estudou bem; vai preparar a valsa.");
        scenario.processQueue();
        API_BOTS.resetRequests();
        return jdbc.queryForObject("select id from lesson_report", UUID.class);
    }

    // ---- C1

    @Test
    void unlinkedTeacherReceivesCodeRequiredTextWithActionIdByPosition() throws Exception {
        long update = nextUpdate();
        MvcResult result = update(messageJson(update, USER_CLARA, "\"text\": \"oi\""));

        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("version").asString()).isEqualTo("v1");
        assertThat(body.path("updateId").asLong()).isEqualTo(update);
        assertThat(body.path("batchId").asString()).isEqualTo("u" + update);
        JsonNode action = body.get("actions").get(0);
        assertThat(action.path("actionId").asString()).isEqualTo("u" + update + "-0");
        assertThat(action.path("type").asString()).isEqualTo("MESSAGE");
        assertThat(action.path("text").asString()).startsWith("Olá! Para usar o bot");
        assertThat(action.path("chatId").asLong()).isEqualTo(USER_CLARA);
        // nada foi enviado ao Telegram: o envio é do orquestrador (FR-010)
        assertThat(sentTexts()).isEmpty();
    }

    @Test
    void groupOnlyReceivesPrivateConversation() throws Exception {
        MvcResult result =
                update(messageJson(nextUpdate(), USER_CLARA, "group", "\"text\": \"K7QM2XPA\""));
        assertThat(actions(result).get(0).path("text").asString())
                .isEqualTo("Eu só atendo em conversa privada. Fale comigo diretamente.");
    }

    @Test
    void unreadableBodyOrUnsupportedTypeReturnsEmptyList() throws Exception {
        for (String body :
                List.of(
                        "{ nao é json",
                        "",
                        "{}",
                        "{\"update_id\": " + nextUpdate() + ", \"edited_message\": {}}")) {
            MvcResult result = update(body);
            assertThat(actions(result)).isEmpty();
        }
    }

    // ---- erro inesperado (FR-008, FR-039)

    @Test
    void interpreterThatThrowsReturnsInternalErrorForTextAndTouchAndRepetitionReturnsSameReply()
            throws Exception {
        doThrow(new IllegalStateException("falha com o aluno Matteo Ferreira"))
                .when(interpreter)
                .interpret(any());

        long textId = nextUpdate();
        String text = messageJson(textId, USER_CLARA, "\"text\": \"oi\"");
        JsonNode byText = actions(update(text));
        assertThat(byText).hasSize(1);
        assertThat(byText.get(0).path("type").asString()).isEqualTo("MESSAGE");
        assertThat(byText.get(0).path("text").asString()).isEqualTo(INTERNAL_ERROR_TEXT);
        assertThat(byText.get(0).path("chatId").asLong()).isEqualTo(USER_CLARA);
        assertThat(byText.get(0).path("actionId").asString()).isEqualTo("u" + textId + "-0");
        assertThat(actions(update(text))).isEqualTo(byText);

        long touchId = nextUpdate();
        String touch =
                """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": "v1:home:",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(touchId, touchId, USER_CLARA, USER_CLARA);
        JsonNode byTouch = actions(update(touch));
        assertThat(byTouch).hasSize(2);
        assertThat(byTouch.get(0).path("type").asString()).isEqualTo("CALLBACK_REPLY");
        assertThat(byTouch.get(0).path("callbackId").asString()).isEqualTo("cb" + touchId);
        assertThat(byTouch.get(1).path("text").asString()).isEqualTo(INTERNAL_ERROR_TEXT);
        assertThat(byTouch.get(0).path("actionId").asString()).isEqualTo("u" + touchId + "-0");
        assertThat(byTouch.get(1).path("actionId").asString()).isEqualTo("u" + touchId + "-1");
        assertThat(actions(update(touch))).isEqualTo(byTouch);
    }

    @Test
    void mappingThatThrowsReturnsInternalErrorWithStableIdsAndSameReplyOnRepeat() throws Exception {
        doThrow(new IllegalStateException("falha com o aluno Matteo Ferreira"))
                .doCallRealMethod()
                .when(mapper)
                .map(any(), anyLong(), any());
        long textId = nextUpdate();
        String text = messageJson(textId, USER_CLARA, "\"text\": \"oi\"");

        JsonNode byText = actions(update(text));
        assertThat(byText).hasSize(1);
        assertThat(byText.get(0).path("text").asString()).isEqualTo(INTERNAL_ERROR_TEXT);
        assertThat(byText.get(0).path("actionId").asString()).isEqualTo("u" + textId + "-e0");
        assertThat(actions(update(text))).isEqualTo(byText);

        doThrow(new IllegalStateException("falha com o aluno Matteo Ferreira"))
                .doCallRealMethod()
                .when(mapper)
                .map(any(), anyLong(), any());
        long touchId = nextUpdate();
        String touch =
                """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": "v1:home:",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(touchId, touchId, USER_CLARA, USER_CLARA);
        JsonNode byTouch = actions(update(touch));
        assertThat(byTouch).hasSize(2);
        assertThat(byTouch.get(0).path("type").asString()).isEqualTo("CALLBACK_REPLY");
        assertThat(byTouch.get(0).path("actionId").asString()).isEqualTo("u" + touchId + "-e0");
        assertThat(byTouch.get(1).path("text").asString()).isEqualTo(INTERNAL_ERROR_TEXT);
        assertThat(byTouch.get(1).path("actionId").asString()).isEqualTo("u" + touchId + "-e1");
        assertThat(actions(update(touch))).isEqualTo(byTouch);
    }

    // ---- C2

    @Test
    void touchOnApproveBringsCallbackReplyThenConfirmationWithButtons() throws Exception {
        prepareReview();

        MvcResult result = callback(USER_CLARA, "v1:approve:1");

        JsonNode actions = actions(result);
        assertThat(actions.get(0).path("type").asString()).isEqualTo("CALLBACK_REPLY");
        assertThat(actions.get(0).path("callbackId").asString()).startsWith("cb");
        JsonNode confirmation = actions.get(1);
        assertThat(confirmation.path("type").asString()).isEqualTo("MESSAGE");
        assertThat(confirmation.path("keyboard").path("rows")).isNotEmpty();
        assertThat(confirmation.path("keyboard").toString()).contains("v1:");
    }

    // ---- C3

    @Test
    void readyReportBringsSplitMessagesDocumentAndButtonsInTodaysOrder() throws Exception {
        UUID report = prepareReview();
        jdbc.update(
                "update lesson_report set content = jsonb_set(content, '{observations}',"
                        + " to_jsonb(repeat('Observação importante da aula. ', 200)))");

        MvcResult result = callback(USER_CLARA, "v1:focus:a" + report);

        JsonNode actions = actions(result);
        List<String> types = new ArrayList<>();
        actions.forEach(a -> types.add(a.path("type").asString()));
        int document = types.indexOf("DOCUMENT");
        assertThat(document).as("tipos %s", types).isGreaterThan(1);
        assertThat(types.subList(1, document)).containsOnly("MESSAGE");
        assertThat(types.subList(1, document).size())
                .as("texto longo dividido em mais de uma mensagem")
                .isGreaterThanOrEqualTo(2);
        for (int i = 1; i < document; i++) {
            assertThat(actions.get(i).path("text").asString().length()).isLessThanOrEqualTo(4096);
        }
        JsonNode pdf = actions.get(document);
        assertThat(pdf.path("documentId").asString()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(pdf.path("fileName").asString()).endsWith(".pdf");
        assertThat(pdf.path("contentType").asString()).isEqualTo("application/pdf");
        assertThat(pdf.has("content")).isFalse();
        JsonNode last = actions.get(actions.size() - 1);
        assertThat(document).isEqualTo(actions.size() - 2);
        assertThat(last.path("type").asString()).isEqualTo("MESSAGE");
        assertThat(last.path("keyboard").toString())
                .contains("v1:approve:1", "v1:change:", "v1:pdf:", "v1:cancel:", "v1:register:");
        // ids únicos e por posição, depois da divisão
        List<String> ids = new ArrayList<>();
        actions.forEach(a -> ids.add(a.path("actionId").asString()));
        assertThat(ids).doesNotHaveDuplicates();
    }

    private JsonNode actionCompensation(String actionId) throws Exception {
        String json =
                jdbc.queryForObject(
                        "select compensation::text from channel_action where id = ?",
                        String.class,
                        actionId);
        assertThat(json).as("compensação gravada para " + actionId).isNotNull();
        return JSON.readTree(json);
    }

    @Test
    void reviewPdfKeepsNoticeWithViewPdfButtonToAskAgain() throws Exception {
        UUID report = prepareReview();

        JsonNode actions = actions(callback(USER_CLARA, "v1:focus:a" + report));

        JsonNode pdf = null;
        for (JsonNode action : actions) {
            if ("DOCUMENT".equals(action.path("type").asString())) {
                pdf = action;
            }
        }
        assertThat(pdf).as("ação de documento").isNotNull();
        JsonNode compensation = actionCompensation(pdf.path("actionId").asString());
        assertThat(compensation.path("type").asString()).isEqualTo("MESSAGE");
        assertThat(compensation.path("chatId").asLong()).isEqualTo(USER_CLARA);
        assertThat(compensation.path("text").asString())
                .isEqualTo(
                        "Não consegui enviar o PDF agora. Toque no botão abaixo para pedir de novo.");
        assertThat(compensation.path("keyboard").path("rows")).hasSize(1);
        assertThat(compensation.path("keyboard").toString())
                .contains("\"label\":\"Ver PDF\"", "\"data\":\"v1:pdf:\"");
        assertThat(compensation.toString())
                .doesNotContain("documentId", "Matteo", pdf.path("documentId").asString());
    }

    @Test
    void historyOfficialPdfKeepsNoticeWithItemButton() throws Exception {
        Teacher clara = scenario.clara();
        scenario.approvedLesson(
                clara,
                scenario.student(clara, "Matteo Ferreira", "Piano"),
                java.time.LocalDate.of(2026, 3, 1),
                "Aula");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        sendText(USER_CLARA, "histórico");
        sendText(USER_CLARA, "Matteo");

        JsonNode actions = actions(callback(USER_CLARA, "v1:history-item:0"));

        JsonNode pdf = actions.get(actions.size() - 1);
        assertThat(pdf.path("type").asString()).isEqualTo("DOCUMENT");
        JsonNode compensation = actionCompensation(pdf.path("actionId").asString());
        assertThat(compensation.path("text").asString())
                .isEqualTo(
                        "Não consegui enviar o PDF agora. Toque no botão abaixo para pedir de novo.");
        assertThat(compensation.path("keyboard").toString())
                .contains("\"label\":\"Ver PDF\"", "\"data\":\"v1:history-item:0\"")
                .doesNotContain("v1:pdf:");
        assertThat(compensation.toString()).doesNotContain("documentId");
    }

    @Test
    void actionDocumentHasSameNameAndSamePdfAsDirectSending() throws Exception {
        prepareReview();
        sendCallback(USER_CLARA, "v1:pdf:"); // envio direto, pelo webhook
        var direct =
                API_BOTS.findAll(
                        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching(
                                        ".*/sendDocument")));
        assertThat(direct).hasSize(1);
        String multipart = direct.get(0).getBodyAsString();
        String directName = multipart.replaceAll("(?s).*filename=\"([^\"]+)\".*", "$1");

        JsonNode pdf = null;
        for (JsonNode a : actions(callback(USER_CLARA, "v1:pdf:"))) {
            if (a.path("type").asString().equals("DOCUMENT")) {
                pdf = a;
            }
        }
        assertThat(pdf).isNotNull();
        assertThat(pdf.path("fileName").asString()).isEqualTo(directName);
        assertThat(pdf.has("caption")).isFalse();
    }

    // ---- confirmações e estado

    @Test
    void confirmationsAndActionStateFollowContract() throws Exception {
        MvcResult result = update(messageJson(nextUpdate(), USER_CLARA, "\"text\": \"oi\""));
        String actionId = actions(result).get(0).path("actionId").asString();

        MvcResult before =
                mvc.perform(
                                get(BASE + "/actions/" + actionId)
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE))
                        .andReturn();
        assertThat(before.getResponse().getStatus()).isEqualTo(200);
        ActionsSchemaV1.require("ActionStateResponse", before.getResponse().getContentAsString());
        ChannelOpenApiContract.requireReply(before);
        assertThat(
                        JSON.readTree(before.getResponse().getContentAsString())
                                .path("state")
                                .asString())
                .isEqualTo("DELIVERED");

        MvcResult confirmation =
                mvc.perform(
                                post(BASE + "/confirmations")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"version\":\"v1\",\"newField\":true,\"results\":[{\"actionId\":\""
                                                        + actionId
                                                        + "\",\"outcome\":\"EXECUTED\",\"extra\":1},"
                                                        + "{\"actionId\":\"u1-9\",\"outcome\":\"EXECUTED\"}]}"))
                        .andReturn();
        assertThat(confirmation.getResponse().getStatus()).isEqualTo(200);
        ActionsSchemaV1.require(
                "ConfirmationResponse", confirmation.getResponse().getContentAsString());
        ChannelOpenApiContract.requireReply(confirmation);
        JsonNode body = JSON.readTree(confirmation.getResponse().getContentAsString());
        assertThat(body.path("accepted").asInt()).isEqualTo(1);
        assertThat(body.path("ignored")).hasSize(1);

        MvcResult after =
                mvc.perform(
                                get(BASE + "/actions/" + actionId)
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE))
                        .andReturn();
        assertThat(JSON.readTree(after.getResponse().getContentAsString()).path("state").asString())
                .isEqualTo("EXECUTED");

        MvcResult unknown =
                mvc.perform(
                                get(BASE + "/actions/u1-999999")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE))
                        .andReturn();
        assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
        assertThat(unknown.getResponse().getContentAsByteArray()).isEmpty();
        ChannelOpenApiContract.requireReply(unknown);
    }

    @Test
    void failureWithCodeRecordsReasonAndUnknownVersionIsRefused() throws Exception {
        String actionId =
                actions(update(messageJson(nextUpdate(), USER_CLARA, "\"text\": \"oi\"")))
                        .get(0)
                        .path("actionId")
                        .asString();
        MvcResult failure =
                mvc.perform(
                                post(BASE + "/confirmations")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"version\":\"v1\",\"results\":[{\"actionId\":\""
                                                        + actionId
                                                        + "\",\"outcome\":\"FAILED\",\"reasonCode\":\"CHAT_UNAVAILABLE\"}]}"))
                        .andReturn();
        assertThat(failure.getResponse().getStatus()).isEqualTo(200);
        var state =
                JSON.readTree(
                        mvc.perform(
                                        get(BASE + "/actions/" + actionId)
                                                .header("X-N8N-Service-Token", TOKEN_SERVICE))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(state.path("state").asString()).isEqualTo("FAILED");
        assertThat(state.path("reasonCode").asString()).isEqualTo("CHAT_UNAVAILABLE");

        for (String body :
                List.of(
                        "{\"version\":\"v2\",\"results\":[]}",
                        "{\"results\":[]}",
                        "não é json",
                        "{\"version\":\"v1\"}",
                        "{\"version\":\"v1\",\"results\":[]}")) {
            MvcResult refused =
                    mvc.perform(
                                    post(BASE + "/confirmations")
                                            .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))
                            .andReturn();
            assertThat(refused.getResponse().getStatus()).as(body).isEqualTo(400);
            ActionsSchemaV1.require("Error", refused.getResponse().getContentAsString());
            ChannelOpenApiContract.requireReply(refused);
        }
        MvcResult version =
                mvc.perform(
                                post(BASE + "/confirmations")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"version\":\"v2\",\"results\":[]}"))
                        .andReturn();
        assertThat(
                        JSON.readTree(version.getResponse().getContentAsString())
                                .path("code")
                                .asString())
                .isEqualTo("UNSUPPORTED_VERSION");
    }

    @Test
    void error401HasDocumentedCodeAndNoBody() throws Exception {
        MvcResult withoutToken =
                mvc.perform(
                                post(BASE + "/update")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(withoutToken.getResponse().getStatus()).isEqualTo(401);
        ChannelOpenApiContract.requireReply(withoutToken);
    }

    // ---- latência (SC-003): mede e imprime; não bloqueia o build

    @Test
    @Tag("latency")
    void p95LatencyOfUpdateWithReviewAndPdf() throws Exception {
        UUID report = prepareReview();
        List<Long> durationsMs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            long start = System.nanoTime();
            callback(USER_CLARA, "v1:focus:a" + report);
            durationsMs.add((System.nanoTime() - start) / 1_000_000);
        }
        Collections.sort(durationsMs);
        long p95 = durationsMs.get((int) Math.ceil(durationsMs.size() * 0.95) - 1);
        System.out.printf(
                "LATENCIA /atualizacao (conferencia com PDF, 50 chamadas): p50=%d ms p95=%d ms max=%d ms (alvo SC-003: p95 <= 2000 ms)%n",
                durationsMs.get(durationsMs.size() / 2), p95, durationsMs.getLast());
    }
}
