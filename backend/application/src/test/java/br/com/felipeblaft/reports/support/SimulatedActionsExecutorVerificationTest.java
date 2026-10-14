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
                                        "{\"versao\":\"v1\",\"aceitas\":1,\"ignoradas\":[]}"
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
                        [{"acaoId":"u1-0","tipo":"FUTURA","campoNovo":1},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"oi","campoExtra":"x"}]
                        """));

        assertThat(executor.informed)
                .containsExactly(
                        new Result("u1-0", "FALHOU", "ACAO_NAO_SUPORTADA", null),
                        new Result("u1-1", "EXECUTADA", null, 77L));
        apiBots.verify(
                1,
                postRequestedFor(urlPathEqualTo("/botT/sendMessage"))
                        .withRequestBody(
                                equalToJson("{\"chat_id\":5,\"text\":\"oi\"}", true, true)));
        assertThat(confirmations).hasSize(1);
    }

    @Test
    void doesNotStopAtFirstFailure() throws Exception {
        telegramError("editMessageText", 403, "Forbidden: bot was blocked by the user");
        executor.run(
                actions(
                        """
                        [{"acaoId":"u1-0","tipo":"EDICAO","chatId":5,"mensagemId":9,"texto":"a"},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"b"}]
                        """));
        assertThat(executor.informed)
                .containsExactly(
                        new Result("u1-0", "FALHOU", "CHAT_INDISPONIVEL", null),
                        new Result("u1-1", "EXECUTADA", null, 77L));
    }

    @Test
    void classifiesTelegramErrorsAccordingToContractTable() {
        assertThat(
                        SimulatedActionsExecutorTest.classify(
                                400, "Bad Request: message is not modified"))
                .isNull();
        assertThat(
                        SimulatedActionsExecutorTest.classify(
                                400, "Bad Request: message to edit not found"))
                .isEqualTo("MENSAGEM_NAO_EDITAVEL");
        assertThat(
                        SimulatedActionsExecutorTest.classify(
                                400, "Bad Request: message can't be edited"))
                .isEqualTo("MENSAGEM_NAO_EDITAVEL");
        assertThat(SimulatedActionsExecutorTest.classify(403, "Forbidden"))
                .isEqualTo("CHAT_INDISPONIVEL");
        assertThat(SimulatedActionsExecutorTest.classify(400, "Bad Request: chat not found"))
                .isEqualTo("CHAT_INDISPONIVEL");
        assertThat(SimulatedActionsExecutorTest.classify(429, "Too Many Requests"))
                .isEqualTo("LIMITE_DE_TAXA");
        assertThat(SimulatedActionsExecutorTest.classify(500, null)).isEqualTo("ERRO_TELEGRAM");
        assertThat(SimulatedActionsExecutorTest.classify(400, "algo inesperado"))
                .isEqualTo("ERRO_TELEGRAM");
    }

    @Test
    void unmodifiedMessageInEditIsConfirmedAsExecuted() throws Exception {
        telegramError("editMessageText", 400, "Bad Request: message is not modified");
        executor.run(
                actions(
                        "[{\"acaoId\":\"u1-0\",\"tipo\":\"EDICAO\",\"chatId\":5,\"mensagemId\":9,\"texto\":\"a\"}]"));
        assertThat(executor.informed).containsExactly(new Result("u1-0", "EXECUTADA", null, 9L));
    }

    @Test
    void editOfMissingMessageBecomesNonEditableMessageAndRateLimitIsSeparate() throws Exception {
        telegramError("editMessageText", 400, "Bad Request: message to edit not found");
        telegramError("sendMessage", 429, "Too Many Requests: retry after 5");
        executor.run(
                actions(
                        """
                        [{"acaoId":"u1-0","tipo":"EDICAO","chatId":5,"mensagemId":9,"texto":"a"},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"b"}]
                        """));
        assertThat(executor.informed)
                .containsExactly(
                        new Result("u1-0", "FALHOU", "MENSAGEM_NAO_EDITAVEL", null),
                        new Result("u1-1", "FALHOU", "LIMITE_DE_TAXA", null));
    }

    @Test
    void actionAlreadyHandledAndSkippedByBackendQuery() throws Exception {
        responsesGet.put(
                "/interno/v1/canal/telegram/acoes/u1-0",
                new InternalClient.Response(
                        200,
                        "{\"versao\":\"v1\",\"acaoId\":\"u1-0\",\"estado\":\"EXECUTADA\"}"
                                .getBytes(StandardCharsets.UTF_8)));
        responsesGet.put(
                "/interno/v1/canal/telegram/acoes/u1-1",
                new InternalClient.Response(
                        200,
                        "{\"versao\":\"v1\",\"acaoId\":\"u1-1\",\"estado\":\"ENTREGUE\"}"
                                .getBytes(StandardCharsets.UTF_8)));
        executor.run(
                actions(
                        """
                        [{"acaoId":"u1-0","tipo":"MENSAGEM","chatId":5,"texto":"a"},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"b"}]
                        """));
        apiBots.verify(1, postRequestedFor(urlPathEqualTo("/botT/sendMessage")));
        assertThat(executor.informed).extracting(Result::actionId).containsExactly("u1-1");
    }

    @Test
    void unavailableDocumentMakesSingleAttemptAndConfirmsFailure() throws Exception {
        executor.run(
                actions(
                        "[{\"acaoId\":\"u1-0\",\"tipo\":\"DOCUMENTO\",\"chatId\":5,"
                                + "\"documentoId\":\""
                                + "A".repeat(43)
                                + "\",\"nomeArquivo\":\"r.pdf\","
                                + "\"tipoConteudo\":\"application/pdf\"}]"));
        assertThat(executor.informed)
                .containsExactly(new Result("u1-0", "FALHOU", "DOCUMENTO_INDISPONIVEL", null));
        assertThat(callsGet.stream().filter(c -> c.contains("/documentos/")).count()).isEqualTo(1);
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
                "/interno/v1/canal/telegram/documentos/" + "A".repeat(43),
                new InternalClient.Response(
                        200, "%PDF-1.7 conteudo".getBytes(StandardCharsets.UTF_8)));
        executor.run(
                actions(
                        "[{\"acaoId\":\"u1-0\",\"tipo\":\"DOCUMENTO\",\"chatId\":5,"
                                + "\"documentoId\":\""
                                + "A".repeat(43)
                                + "\",\"nomeArquivo\":\"r.pdf\","
                                + "\"tipoConteudo\":\"application/pdf\",\"legenda\":\"Segue\"}]"));
        assertThat(executor.informed).extracting(Result::status).containsExactly("EXECUTADA");
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
                        [{"acaoId":"p:e:0","tipo":"MENSAGEM_ETAPA","chatId":5,"texto":"a","trabalhoId":"t"},
                         {"acaoId":"p:e:1","tipo":"MENSAGEM_ETAPA","chatId":5,"texto":"b","trabalhoId":"t","editarMensagemId":55}]
                        """));
        apiBots.verify(1, postRequestedFor(urlPathEqualTo("/botT/sendMessage")));
        apiBots.verify(1, postRequestedFor(urlPathEqualTo("/botT/editMessageText")));
        assertThat(executor.informed)
                .containsExactly(
                        new Result("p:e:0", "EXECUTADA", null, 77L),
                        new Result("p:e:1", "EXECUTADA", null, 55L));
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
                        [{"acaoId":"u1-0","tipo":"RESPOSTA_CALLBACK","callbackId":"cb1"},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"x",
                          "teclado":{"linhas":[[{"rotulo":"Sim","dados":"v1:sim:"}]]}}]
                        """));
        apiBots.verify(
                postRequestedFor(urlPathEqualTo("/botT/answerCallbackQuery"))
                        .withRequestBody(equalToJson("{\"callback_query_id\":\"cb1\"}")));
        apiBots.verify(
                postRequestedFor(urlPathEqualTo("/botT/sendMessage"))
                        .withRequestBody(
                                equalToJson(
                                        "{\"reply_markup\":{\"inline_keyboard\":[[{\"text\":\"Sim\",\"callback_data\":\"v1:sim:\"}]]}}",
                                        true,
                                        true)));
    }

    @Test
    void withoutActionsConfirmsNothing() throws Exception {
        executor.run(actions("[]"));
        assertThat(confirmations).isEmpty();
    }
}
