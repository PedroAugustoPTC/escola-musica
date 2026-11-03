package br.com.felipeblaft.reports.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.SimulatedActionsExecutorTest.InternalClient;
import br.com.felipeblaft.reports.support.SimulatedActionsExecutorTest.Result;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O executor simulado é a especificação executável do workflow do orquestrador: este teste garante
 * que ele segue as obrigações do contrato (§4) antes de servir de régua para o backend (FR-003a).
 */
class SimulatedActionsExecutorVerificationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private WireMockServer apiBots;
    private final Map<String, InternalClient.Response> responsesGet = new HashMap<>();
    private final List<String> callsGet = new ArrayList<>();
    private final List<String> confirmations = new ArrayList<>();
    private SimulatedActionsExecutorTest executor;

    @BeforeEach
    void prepare() {
        apiBots = new WireMockServer(wireMockConfig().dynamicPort());
        apiBots.start();
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/sendMessage"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":{\"message_id\":77}}")));
        executor =
                new SimulatedActionsExecutorTest(
                        new InternalClient() {
                            @Override
                            public Response get(String path) {
                                callsGet.add(path);
                                return responsesGet.getOrDefault(
                                        path, new Response(404, new byte[0]));
                            }

                            @Override
                            public Response post(String path, String bodyJson) {
                                confirmations.add(bodyJson);
                                return new Response(
                                        200,
                                        "{\"version\":\"v1\",\"accepted\":1,\"ignored\":[]}"
                                                .getBytes(StandardCharsets.UTF_8));
                            }
                        },
                        apiBots.baseUrl() + "/botT");
    }

    @AfterEach
    void end() {
        apiBots.stop();
    }

    private static JsonNode actions(String json) {
        return JSON.readTree(json);
    }

    private void telegramError(String method, int status, String description) {
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/" + method))
                        .willReturn(
                                aResponse()
                                        .withStatus(status)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"ok\":false,\"description\":\""
                                                        + description
                                                        + "\"}")));
    }

    @Test
    void unknownTypeIsSkippedConfirmedAsUnsupportedAndExtraFieldIsIgnored() throws Exception {
        executor.run(
                actions(
                        """
                        [{"actionId":"u1-0","type":"FUTURE","newField":1},
                         {"actionId":"u1-1","type":"MESSAGE","chatId":5,"text":"oi","extraField":"x"}]
                        """));

        assertThat(executor.informed)
                .containsExactly(
                        new Result("u1-0", "FAILED", "ACTION_NOT_SUPPORTED", null),
                        new Result("u1-1", "EXECUTED", null, 77L));
        apiBots.verify(
                1,
                postRequestedFor(urlPathEqualTo("/botT/sendMessage"))
                        .withRequestBody(
                                equalToJson("{\"chat_id\":5,\"text\":\"oi\"}", true, true)));
        assertThat(confirmations).hasSize(1);
    }

    @Test
    void deleteMessageCallsTheBotApiAndConfirmsExecuted() throws Exception {
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/deleteMessage"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":true}")));

        executor.run(
                actions(
                        """
                        [{"actionId":"u2-0","type":"DELETE_MESSAGE","chatId":5,"messageId":89}]
                        """));

        assertThat(executor.informed).containsExactly(new Result("u2-0", "EXECUTED", null, null));
        apiBots.verify(
                1,
                postRequestedFor(urlPathEqualTo("/botT/deleteMessage"))
                        .withRequestBody(
                                equalToJson("{\"chat_id\":5,\"message_id\":89}", true, true)));
    }

    @Test
    void deleteMessageRefusedByTelegramIsInformedAsNotDeletable() throws Exception {
        telegramError("deleteMessage", 400, "Bad Request: message can't be deleted");

        executor.run(
                actions(
                        """
                        [{"actionId":"u3-0","type":"DELETE_MESSAGE","chatId":5,"messageId":89},
                         {"actionId":"u3-1","type":"MESSAGE","chatId":5,"text":"b"}]
                        """));

        assertThat(executor.informed)
                .containsExactly(
                        new Result("u3-0", "FAILED", "MESSAGE_NOT_DELETABLE", null),
                        new Result("u3-1", "EXECUTED", null, 77L));
    }

    @Test
    void doesNotStopAtFirstFailure() throws Exception {
        telegramError("editMessageText", 403, "Forbidden: bot was blocked by the user");
        executor.run(
                actions(
                        """
                        [{"actionId":"u1-0","type":"EDIT","chatId":5,"messageId":9,"text":"a"},
                         {"actionId":"u1-1","type":"MESSAGE","chatId":5,"text":"b"}]
                        """));
        assertThat(executor.informed)
                .containsExactly(
                        new Result("u1-0", "FAILED", "CHAT_UNAVAILABLE", null),
                        new Result("u1-1", "EXECUTED", null, 77L));
    }

    static java.util.stream.Stream<ClassificationCases.Case> classificationCases() {
        return ClassificationCases.all().stream();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("classificationCases")
    void classifiesTelegramErrorsAccordingToContractTable(ClassificationCases.Case scenario) {
        assertThat(SimulatedActionsExecutorTest.classify(scenario.status(), scenario.description()))
                .isEqualTo(scenario.reasonCode());
    }

    @Test
    void unmodifiedMessageInEditIsConfirmedAsExecuted() throws Exception {
        telegramError("editMessageText", 400, "Bad Request: message is not modified");
        executor.run(
                actions(
                        "[{\"actionId\":\"u1-0\",\"type\":\"EDIT\",\"chatId\":5,\"messageId\":9,\"text\":\"a\"}]"));
        assertThat(executor.informed).containsExactly(new Result("u1-0", "EXECUTED", null, 9L));
    }

    @Test
    void editOfMissingMessageBecomesNonEditableMessageAndRateLimitIsSeparate() throws Exception {
        telegramError("editMessageText", 400, "Bad Request: message to edit not found");
        telegramError("sendMessage", 429, "Too Many Requests: retry after 5");
        executor.run(
                actions(
                        """
                        [{"actionId":"u1-0","type":"EDIT","chatId":5,"messageId":9,"text":"a"},
                         {"actionId":"u1-1","type":"MESSAGE","chatId":5,"text":"b"}]
                        """));
        assertThat(executor.informed)
                .containsExactly(
                        new Result("u1-0", "FAILED", "MESSAGE_NOT_EDITABLE", null),
                        new Result("u1-1", "FAILED", "RATE_LIMITED", null));
    }

    @Test
    void actionAlreadyHandledAndSkippedByBackendQuery() throws Exception {
        responsesGet.put(
                "/internal/v1/channel/telegram/actions/u1-0",
                new InternalClient.Response(
                        200,
                        "{\"version\":\"v1\",\"actionId\":\"u1-0\",\"state\":\"EXECUTED\"}"
                                .getBytes(StandardCharsets.UTF_8)));
        responsesGet.put(
                "/internal/v1/channel/telegram/actions/u1-1",
                new InternalClient.Response(
                        200,
                        "{\"version\":\"v1\",\"actionId\":\"u1-1\",\"state\":\"DELIVERED\"}"
                                .getBytes(StandardCharsets.UTF_8)));
        executor.run(
                actions(
                        """
                        [{"actionId":"u1-0","type":"MESSAGE","chatId":5,"text":"a"},
                         {"actionId":"u1-1","type":"MESSAGE","chatId":5,"text":"b"}]
                        """));
        apiBots.verify(1, postRequestedFor(urlPathEqualTo("/botT/sendMessage")));
        assertThat(executor.informed).extracting(Result::actionId).containsExactly("u1-1");
    }

    @Test
    void unavailableDocumentMakesSingleAttemptAndConfirmsFailure() throws Exception {
        executor.run(
                actions(
                        "[{\"actionId\":\"u1-0\",\"type\":\"DOCUMENT\",\"chatId\":5,"
                                + "\"documentId\":\""
                                + "A".repeat(43)
                                + "\",\"fileName\":\"r.pdf\","
                                + "\"contentType\":\"application/pdf\"}]"));
        assertThat(executor.informed)
                .containsExactly(new Result("u1-0", "FAILED", "DOCUMENT_UNAVAILABLE", null));
        assertThat(callsGet.stream().filter(c -> c.contains("/documents/")).count()).isEqualTo(1);
        apiBots.verify(0, postRequestedFor(urlPathEqualTo("/botT/sendDocument")));
    }

    @Test
    void availableDocumentGoesAsMultipartWithBytes() throws Exception {
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/sendDocument"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":{\"message_id\":3}}")));
        responsesGet.put(
                "/internal/v1/channel/telegram/documents/" + "A".repeat(43),
                new InternalClient.Response(
                        200, "%PDF-1.7 conteudo".getBytes(StandardCharsets.UTF_8)));
        executor.run(
                actions(
                        "[{\"actionId\":\"u1-0\",\"type\":\"DOCUMENT\",\"chatId\":5,"
                                + "\"documentId\":\""
                                + "A".repeat(43)
                                + "\",\"fileName\":\"r.pdf\","
                                + "\"contentType\":\"application/pdf\",\"caption\":\"Segue\"}]"));
        assertThat(executor.informed).extracting(Result::status).containsExactly("EXECUTED");
        var sent = apiBots.findAll(postRequestedFor(urlPathEqualTo("/botT/sendDocument"))).get(0);
        assertThat(sent.getBodyAsString())
                .contains("%PDF-1.7 conteudo", "filename=\"r.pdf\"", "name=\"caption\"", "Segue");
    }

    @Test
    void stepWithoutIdSendsNewMessageAndStepWithIdEditsAndBothReportMessageId() throws Exception {
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/editMessageText"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":true}")));
        executor.run(
                actions(
                        """
                        [{"actionId":"p:e:0","type":"STEP_MESSAGE","chatId":5,"text":"a","jobId":"t"},
                         {"actionId":"p:e:1","type":"STEP_MESSAGE","chatId":5,"text":"b","jobId":"t","editMessageId":55}]
                        """));
        apiBots.verify(1, postRequestedFor(urlPathEqualTo("/botT/sendMessage")));
        apiBots.verify(1, postRequestedFor(urlPathEqualTo("/botT/editMessageText")));
        assertThat(executor.informed)
                .containsExactly(
                        new Result("p:e:0", "EXECUTED", null, 77L),
                        new Result("p:e:1", "EXECUTED", null, 55L));
    }

    @Test
    void keyboardBecomesReplyMarkupAndCallbackReplyGoesToAnswerCallbackQuery() throws Exception {
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/answerCallbackQuery"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":true}")));
        executor.run(
                actions(
                        """
                        [{"actionId":"u1-0","type":"CALLBACK_REPLY","callbackId":"cb1"},
                         {"actionId":"u1-1","type":"MESSAGE","chatId":5,"text":"x",
                          "keyboard":{"rows":[[{"label":"Sim","data":"v1:yes:"}]]}}]
                        """));
        apiBots.verify(
                postRequestedFor(urlPathEqualTo("/botT/answerCallbackQuery"))
                        .withRequestBody(equalToJson("{\"callback_query_id\":\"cb1\"}")));
        apiBots.verify(
                postRequestedFor(urlPathEqualTo("/botT/sendMessage"))
                        .withRequestBody(
                                equalToJson(
                                        "{\"reply_markup\":{\"inline_keyboard\":[[{\"text\":\"Sim\",\"callback_data\":\"v1:yes:\"}]]}}",
                                        true,
                                        true)));
    }

    @Test
    void withoutActionsConfirmsNothing() throws Exception {
        executor.run(actions("[]"));
        assertThat(confirmations).isEmpty();
    }
}
