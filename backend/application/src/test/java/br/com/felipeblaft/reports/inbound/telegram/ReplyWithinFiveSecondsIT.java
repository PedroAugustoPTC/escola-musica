package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.support.OrchestratorDeliveryBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

/**
 * SC-007: a confirmação e a mensagem de erro de uma ação do professor chegam em até 5 s do
 * recebimento do update, e toda mensagem recebida gera resposta (FR-008). A resposta a um update
 * volta na própria chamada à API interna; o que nasce sem update (proativo) espera, no pior caso,
 * um intervalo do despachante da outbox (2 s por padrão) mais a entrega. Mede-se o tempo de relógio
 * do recebimento até a mensagem aparecer na API de bots, com o executor simulado no lugar do n8n.
 */
class ReplyWithinFiveSecondsIT extends OrchestratorDeliveryBase {

    private static final Duration LIMIT = Duration.ofSeconds(5);
    private static final String BASE = "/internal/v1/channel/telegram";

    @MockitoSpyBean TelegramConversationInterpreter interpreter;

    private static long dispatcherDefaultIntervalMs() throws Exception {
        String yaml;
        try (var input = new ClassPathResource("application.yaml").getInputStream()) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher found =
                Pattern.compile("dispatch-interval-ms:\\s*\\$\\{[A-Z_]+:(\\d+)\\}").matcher(yaml);
        assertThat(found.find()).as("dispatch-interval-ms no application.yaml").isTrue();
        return Long.parseLong(found.group(1));
    }

    /** Envia o update à API interna e passa as ações ao executor; devolve o tempo gasto. */
    private Duration receiveAndRun(String json, List<JsonNode> receivedActions) throws Exception {
        long start = System.nanoTime();
        String body =
                mvc.perform(
                                post(BASE + "/update")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                        .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode actions = JSON.readTree(body).get("actions");
        orchestratorExecutor().run(actions);
        Duration spent = Duration.ofNanos(System.nanoTime() - start);
        actions.forEach(receivedActions::add);
        return spent;
    }

    private static String touch(long updateId, String data) {
        return """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": "%s",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1,
                             "chat": {"id": %d, "type": "private"}}}}
                """
                .formatted(updateId, updateId, data, USER_CLARA, USER_CLARA);
    }

    @Test
    void teacherActionConfirmationAppearsWithinFiveSeconds() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        API_BOTS.resetRequests();
        List<JsonNode> actions = new ArrayList<>();

        Duration spent = receiveAndRun(touch(nextUpdate(), "v1:home:"), actions);

        assertThat(spent).isLessThanOrEqualTo(LIMIT);
        assertThat(actions).isNotEmpty();
        assertThat(calls("answerCallbackQuery")).hasSize(1);
        assertThat(sentTexts()).isNotEmpty();
    }

    @Test
    void errorMessageAppearsWithinFiveSeconds() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        API_BOTS.resetRequests();
        doThrow(new IllegalStateException("failure")).when(interpreter).interpret(any());
        List<JsonNode> actions = new ArrayList<>();

        Duration spent =
                receiveAndRun(messageJson(nextUpdate(), USER_CLARA, "\"text\": \"oi\""), actions);

        assertThat(spent).isLessThanOrEqualTo(LIMIT);
        assertThat(sentTexts()).hasSize(1);
        assertThat(sentTexts().getFirst())
                .matches("Algo deu errado\\. Informe o código de referência [A-Z0-9]{8} ao suporte\\.");
    }

    @Test
    void everyReceivedMessageGeneratesReply() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        List<String> messages =
                List.of(
                        "\"text\": \"oi\"",
                        "\"text\": \"/start\"",
                        "\"text\": \"texto qualquer sem aluno\"",
                        "\"sticker\": {\"file_id\": \"s1\", \"file_unique_id\": \"u-s1\","
                                + " \"width\": 1, \"height\": 1, \"type\": \"regular\","
                                + " \"is_animated\": false, \"is_video\": false}",
                        "\"location\": {\"latitude\": 1.0, \"longitude\": 2.0}");
        for (String content : messages) {
            List<JsonNode> actions = new ArrayList<>();
            Duration spent = receiveAndRun(messageJson(nextUpdate(), USER_CLARA, content), actions);
            assertThat(actions).as("resposta a " + content).isNotEmpty();
            assertThat(spent).as("tempo de " + content).isLessThanOrEqualTo(LIMIT);
        }
        List<JsonNode> ofGroup = new ArrayList<>();
        receiveAndRun(messageJson(nextUpdate(), USER_CLARA, "group", "\"text\": \"oi\""), ofGroup);
        assertThat(ofGroup).as("resposta no grupo").isNotEmpty();
    }

    @Test
    void proactiveActionArrivesWithinDispatcherDefaultIntervalPlusDelivery() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        API_BOTS.resetRequests();
        // a base de testes desliga o despachante agendado; o padrão de produção vem do
        // application.yaml
        long interval = dispatcherDefaultIntervalMs();
        assertThat(interval).as("intervalo padrão do despachante").isEqualTo(2000L);

        long start = System.nanoTime();
        generateNoticeOf(matteo);
        drain(3);
        Duration delivery = Duration.ofNanos(System.nanoTime() - start);

        assertThat(sentTexts()).isNotEmpty();
        // pior caso: o aviso nasce logo depois de um ciclo e espera um intervalo inteiro
        assertThat(delivery.plusMillis(interval)).isLessThanOrEqualTo(LIMIT);
    }
}
