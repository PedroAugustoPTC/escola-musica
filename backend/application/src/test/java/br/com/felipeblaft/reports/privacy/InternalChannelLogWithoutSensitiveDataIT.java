package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.inbound.telegram.TelegramConversationInterpreter;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.OrchestratorDeliveryBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * V16, FR-029, SC-006: com os logs em DEBUG, a conversa pela API interna, a entrega, a retentativa,
 * a falha definitiva, a compensação e as requisições recusadas não registram CPF, nome de aluno,
 * texto de relatório, transcrição nem payload, e nenhum valor sensível semeado aparece: token de
 * serviço (em formato que o mascarador não reconhece), segredo do webhook do Telegram, token e URL
 * do orquestrador (com segredo no caminho) e um {@code documentoId} real. Também os corpos de
 * {@code 401/404}.
 */
class InternalChannelLogWithoutSensitiveDataIT extends OrchestratorDeliveryBase {

    private static final String RESPONSE = "Resposta sigilosa sobre a rotina de estudo da semana";
    private static final String BASE = "/interno/v1/canal/telegram";

    @Autowired SaveChannelActionsUseCase save;

    @MockitoSpyBean TelegramConversationInterpreter interpreter;

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Logger root;
    private Level rootLevel;
    private Logger application;
    private Level applicationLevel;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        application = (Logger) LoggerFactory.getLogger("br.com.felipeblaft");
        rootLevel = root.getLevel();
        applicationLevel = application.getLevel();
        root.setLevel(Level.INFO);
        application.setLevel(Level.DEBUG);
        events.start();
        root.addAppender(events);
    }

    @AfterEach
    void restoreLogs() {
        root.detachAppender(events);
        events.stop();
        root.setLevel(rootLevel);
        application.setLevel(applicationLevel);
    }

    private String logText() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(events.list)) {
            text.append(event.getFormattedMessage()).append('\n');
            event.getMDCPropertyMap()
                    .forEach((k, v) -> text.append(k).append('=').append(v).append('\n'));
            if (event.getKeyValuePairs() != null) {
                event.getKeyValuePairs()
                        .forEach(p -> text.append(p.key).append('=').append(p.value).append('\n'));
            }
            if (event.getThrowableProxy() != null) {
                text.append(ThrowableProxyUtil.asString(event.getThrowableProxy())).append('\n');
            }
        }
        return text.toString();
    }

    private MvcResult update(String json, String token, String secret) throws Exception {
        var request =
                post(BASE + "/atualizacao").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            request.header("X-N8N-Service-Token", token);
        }
        if (secret != null) {
            request.header("X-Telegram-Bot-Api-Secret-Token", secret);
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode throughInternalApi(String json) throws Exception {
        MvcResult result = update(json, TOKEN_SERVICE, SECRET);
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        orchestratorExecutor().run(body.get("acoes"));
        return body.get("acoes");
    }

    @Test
    void interpreterExceptionWithStudentDataDoesNotAppearInLogsNorInReply() throws Exception {
        doThrow(
                        new IllegalStateException(
                                "Matteo Ferreira CPF 123.456.789-09 Czerny e a valsa",
                                new IllegalArgumentException("causa: Matteo " + RESPONSE)))
                .when(interpreter)
                .interpret(any());
        long update = nextUpdate();

        MvcResult result =
                update(messageJson(update, USER_CLARA, "\"text\": \"oi\""), TOKEN_SERVICE, SECRET);

        String response = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(response).contains("Ocorreu um erro inesperado");
        String logs = logText();
        assertThat(logs)
                .contains("telegram-erro-interpretacao updateId=" + update)
                .contains("IllegalStateException");
        for (String forbidden :
                List.of("Matteo", "123.456.789-09", "Czerny", "valsa", RESPONSE, "causa:")) {
            assertThat(logs).as("log sem " + forbidden).doesNotContain(forbidden);
            assertThat(response).as("resposta sem " + forbidden).doesNotContain(forbidden);
        }
        assertThat(
                        events.list.stream()
                                .filter(e -> e.getFormattedMessage().contains("erro-interpretacao"))
                                .allMatch(e -> e.getThrowableProxy() == null))
                .as("sem stack trace")
                .isTrue();
    }

    @Test
    void conversationDeliveryRetryPermanentFailureAndCompensationDoNotLeakData() throws Exception {
        List<MvcResult> refusedRequests = new java.util.ArrayList<>();

        // ---- requisições recusadas e ilegíveis
        for (MvcResult refused :
                List.of(
                        update("{}", null, SECRET),
                        update("{}", "tok-teste-123", SECRET),
                        update("{}", TOKEN_SERVICE, "segredo-errado"),
                        update("{ dado do aluno Matteo nao e json", TOKEN_SERVICE, SECRET),
                        mvc.perform(
                                        get(BASE + "/documentos/" + "Z".repeat(43))
                                                .header("X-N8N-Service-Token", TOKEN_SERVICE))
                                .andReturn(),
                        mvc.perform(
                                        get(BASE + "/acoes/u0-0")
                                                .header("X-N8N-Service-Token", "tok-teste-123"))
                                .andReturn())) {
            refusedRequests.add(refused);
        }

        // ---- conversa interna: vínculo, áudio, perguntas, respostas, conferência e PDF
        sendText(
                USER_CLARA, ScenarioIT.CODE_CLARA); // vínculo pelo webhook (já coberto no outro IT)
        prepareTelegramFile("aula", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        throughInternalApi(messageJson(nextUpdate(), USER_CLARA, "\"text\": \"Matteo\""));
        throughInternalApi(
                messageJson(
                        nextUpdate(),
                        USER_CLARA,
                        "\"voice\": {\"file_id\": \"aula\", \"file_unique_id\": \"u-aula\", \"mime_type\": \"audio/ogg\", \"file_size\": 1000, \"duration\": 30}"));
        throughInternalApi(
                """
                {"update_id": %d, "callback_query": {"id": "cb1", "data": "v1:confal:",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(nextUpdate(), USER_CLARA, USER_CLARA));
        scenario.processQueue();
        drain(10);
        throughInternalApi(messageJson(nextUpdate(), USER_CLARA, "\"text\": \"" + RESPONSE + "\""));
        scenario.processQueue();
        drain(10);
        JsonNode pdf =
                throughInternalApi(
                                """
                                {"update_id": %d, "callback_query": {"id": "cb2", "data": "v1:pdf:",
                                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                                """
                                        .formatted(nextUpdate(), USER_CLARA, USER_CLARA))
                        .get(1);
        String documentId = pdf.path("documentoId").asString();
        assertThat(documentId).hasSize(43);

        // ---- entrega com retentativa, compensação e falha definitiva
        save.run(
                "p:edicao:1",
                ChannelActionOrigin.PROATIVA,
                null,
                List.of(
                        new NewAction(
                                "p:edicao:1:0",
                                ChannelActionType.EDICAO,
                                USER_CLARA,
                                clara.id(),
                                null,
                                "{\"tipo\":\"EDICAO\",\"chatId\":"
                                        + USER_CLARA
                                        + ",\"mensagemId\":9,\"texto\":\"Texto sigiloso da edição\",\"teclado\":{\"linhas\":[]}}",
                                "{\"tipo\":\"MENSAGEM\",\"chatId\":"
                                        + USER_CLARA
                                        + ",\"texto\":\"Texto sigiloso da edição\"}")));
        answer(503);
        task.dispatch();
        clock.advance(Duration.ofMinutes(2));
        accept();
        API_BOTS.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(
                                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching(
                                        ".*/editMessageText"))
                        .willReturn(
                                com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                                        .withStatus(400)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"ok\":false,\"description\":\"Bad Request: message to edit not found\"}")));
        drain(6);
        save.run(
                "p:edicao:2",
                ChannelActionOrigin.PROATIVA,
                null,
                List.of(
                        new NewAction(
                                "p:edicao:2:0",
                                ChannelActionType.MENSAGEM,
                                USER_CLARA,
                                clara.id(),
                                null,
                                "{\"tipo\":\"MENSAGEM\",\"chatId\":"
                                        + USER_CLARA
                                        + ",\"texto\":\"Texto sigiloso que vai expirar\"}",
                                null)));
        answer(500);
        clock.advance(Duration.ofHours(25));
        task.dispatch();

        // ---- os logs de tudo isso
        String logs = logText();
        assertThat(events.list).as("a captura de logs funcionou").isNotEmpty();
        assertThat(logs).contains("canal-acao-falha-definitiva acaoId=p:edicao:2:0");
        Map<String, String> forbidden =
                Map.ofEntries(
                        Map.entry("nome de aluno", "Matteo"),
                        Map.entry("nome de aluno (Helena)", "Helena"),
                        Map.entry("código de acesso", ScenarioIT.CODE_CLARA),
                        Map.entry("token do bot", TOKEN),
                        Map.entry("token de serviço atual", TOKEN_SERVICE),
                        Map.entry("token de serviço próximo", "token-servico-proximo-teste"),
                        Map.entry("token de serviço errado semeado", "tok-teste-123"),
                        Map.entry("segredo do webhook", SECRET),
                        Map.entry("segredo do webhook errado", "segredo-errado"),
                        Map.entry("token do orquestrador", "segredo-do-orquestrador"),
                        Map.entry("segredo no caminho da URL", "segredo-no-caminho-123"),
                        Map.entry("host e porta da URL", "localhost:" + ORCHESTRATOR.port()),
                        Map.entry("documentoId", documentId),
                        Map.entry("resposta do professor", RESPONSE),
                        Map.entry("texto de ação", "Texto sigiloso"),
                        Map.entry("texto de conferência", "Confira o relatório"),
                        Map.entry("conteúdo do relatório", "Czerny"));
        forbidden.forEach(
                (description, value) -> assertThat(logs).as(description).doesNotContain(value));
        // corpos de 401/404 são vazios; o do update ilegível (200) não ecoa o que foi enviado
        for (MvcResult refused : refusedRequests) {
            int status = refused.getResponse().getStatus();
            String body = refused.getResponse().getContentAsString();
            if (status == 401 || status == 404) {
                assertThat(body).as("corpo do status %d", status).isEmpty();
            } else {
                assertThat(body).doesNotContain("Matteo", "dado do aluno");
            }
        }
        // a URL do orquestrador (com segredo no caminho) nunca aparece em log, nem como parte de
        // exceção
        assertThat(logs).doesNotContain("/webhook/");
    }
}
