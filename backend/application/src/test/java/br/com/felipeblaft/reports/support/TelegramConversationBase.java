package br.com.felipeblaft.reports.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base dos testes de conversa do Telegram (contracts/telegram-conversa.md §7): envia updates JSON
 * ao webhook com o segredo e verifica as chamadas feitas à API de bots simulada no WireMock.
 */
@AutoConfigureMockMvc
public abstract class TelegramConversationBase extends IntegrationBase {

    public static final String SECRET = "segredo-webhook-teste";
    public static final String TOKEN = "123456:TESTE";
    protected static final long USER_CLARA = 7001L;
    protected static final long USER_BRUNO = 7002L;
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    protected static final WireMockServer API_BOTS =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        API_BOTS.start();
    }

    private static final AtomicLong UPDATES = new AtomicLong(1000);
    private static final AtomicLong MESSAGES = new AtomicLong(1);

    /**
     * Transporte exercitado pelos testes de conversa: {@code webhook} (padrão) ou {@code interno}.
     */
    public static final String TRANSPORT = System.getProperty("telegram.transport", "webhook");

    public static final String TOKEN_SERVICE = "token-servico-atual-teste";

    @Autowired protected MockMvc mvc;

    @Autowired protected ScenarioIT scenario;

    @Autowired protected ApplicationProperties properties;

    @DynamicPropertySource
    static void botApi(DynamicPropertyRegistry registration) {
        registration.add("reports.telegram.api-url", API_BOTS::baseUrl);
        registration.add("reports.telegram.token", () -> TOKEN);
        registration.add("reports.telegram.webhook-secret", () -> SECRET);
    }

    @BeforeEach
    void cleanBotApi() {
        API_BOTS.resetAll();
        API_BOTS.stubFor(
                post(anyUrl())
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":{\"message_id\":1}}")));
    }

    // ---------------------------------------------------------------- envio de updates

    protected long nextUpdate() {
        return UPDATES.incrementAndGet();
    }

    protected void sendUpdate(String json) throws Exception {
        if ("internal".equals(TRANSPORT)) {
            sendThroughInternalChannel(json);
            return;
        }
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/telegram/webhook")
                                .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isOk());
    }

    /**
     * Modo {@code interno}: o update vai à API interna do canal (token de serviço e segredo do
     * webhook), a resposta é validada contra o esquema v1 e o executor simulado faz o que o n8n
     * fará, traduzindo as ações para as mesmas chamadas à API de bots que o envio direto faria.
     */
    private void sendThroughInternalChannel(String json) throws Exception {
        var result =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/interno/v1/canal/telegram/atualizacao")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                        .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andExpect(status().isOk())
                        .andReturn();
        String body = result.getResponse().getContentAsString();
        ActionsSchemaV1.require("RespostaAtualizacao", body);
        orchestratorExecutor().run(JSON.readTree(body).get("acoes"));
    }

    /** O "n8n" dos testes, ligado ao MockMvc e à API de bots simulada. */
    protected SimulatedActionsExecutorTest orchestratorExecutor() {
        return new SimulatedActionsExecutorTest(
                new InternalClientMockMvc(mvc, TOKEN_SERVICE), API_BOTS.baseUrl() + "/bot" + TOKEN);
    }

    protected static String messageJson(long updateId, long user, String fieldAndContent) {
        return messageJson(updateId, user, "private", fieldAndContent);
    }

    protected static String messageJson(
            long updateId, long user, String chatType, String fieldAndContent) {
        return """
                {"update_id": %d, "message": {"message_id": %d, "date": 1,
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "chat": {"id": %d, "type": "%s"}, %s}}
                """
                .formatted(
                        updateId,
                        MESSAGES.incrementAndGet(),
                        user,
                        user,
                        chatType,
                        fieldAndContent);
    }

    protected void sendText(long user, String text) throws Exception {
        sendUpdate(messageJson(nextUpdate(), user, "\"text\": " + JSON.writeValueAsString(text)));
    }

    protected void sendCallback(long user, String data) throws Exception {
        sendUpdate(
                """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": %s,
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(
                                nextUpdate(),
                                MESSAGES.incrementAndGet(),
                                JSON.writeValueAsString(data),
                                user,
                                user));
    }

    protected void sendVoice(long user, String fileId, long size) throws Exception {
        sendUpdate(
                messageJson(
                        nextUpdate(),
                        user,
                        "\"voice\": {\"file_id\": \"%s\", \"file_unique_id\": \"u-%s\", \"mime_type\": \"audio/ogg\", \"file_size\": %d, \"duration\": 30}"
                                .formatted(fileId, fileId, size)));
    }

    /**
     * Envia um arquivo de áudio ou vídeo pelo campo indicado (voice, audio, video, video_note,
     * document).
     */
    protected void sendFile(long user, String field, String fileId, String mime, long size)
            throws Exception {
        sendUpdate(
                messageJson(
                        nextUpdate(),
                        user,
                        "\"%s\": {\"file_id\": \"%s\", \"file_unique_id\": \"u-%s\", \"mime_type\": \"%s\", \"file_size\": %d}"
                                .formatted(field, fileId, fileId, mime, size)));
    }

    /**
     * Simula o servidor local da API de bots: grava o arquivo no volume compartilhado e responde ao
     * {@code getFile} com o caminho absoluto (modo {@code --local}).
     */
    protected Path prepareTelegramFile(String fileId, byte[] content) throws Exception {
        Path directory = properties.telegram().filesDir().resolve("voice");
        Files.createDirectories(directory);
        Path file = directory.resolve(fileId + ".oga");
        Files.write(file, content);
        API_BOTS.stubFor(
                post(urlPathMatching("/bot.*/getFile"))
                        .withRequestBody(containing(fileId))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                JSON.writeValueAsString(
                                                        java.util.Map.of(
                                                                "ok",
                                                                true,
                                                                "result",
                                                                java.util.Map.of(
                                                                        "file_id",
                                                                        fileId,
                                                                        "file_path",
                                                                        file.toString()))))));
        return file;
    }

    /** Textos das edições de mensagem (etapas do processamento editadas no lugar). */
    protected static List<String> editedTexts() {
        return calls("editMessageText").stream().map(m -> m.get("text").asString()).toList();
    }

    /** Vincula a conta de teste com o código (sequência 5.1) e limpa as chamadas registradas. */
    protected void link(long user, String code) throws Exception {
        sendText(user, code);
        API_BOTS.resetRequests();
    }

    // ---------------------------------------------------------------- verificações

    protected static List<JsonNode> calls(String method) {
        List<JsonNode> bodies = new ArrayList<>();
        for (LoggedRequest request :
                API_BOTS.findAll(
                        postRequestedFor(
                                urlPathMatching(
                                        "/bot" + TOKEN.replace(":", "\\:") + "/" + method)))) {
            if (request.getHeader("Content-Type") != null
                    && request.getHeader("Content-Type").startsWith("application/json")) {
                bodies.add(JSON.readTree(request.getBodyAsString()));
            } else {
                bodies.add(
                        JSON.createObjectNode()
                                .put("multipart", request.getBodyAsString().length()));
            }
        }
        return bodies;
    }

    protected static List<String> sentTexts() {
        return calls("sendMessage").stream().map(m -> m.get("text").asString()).toList();
    }

    protected static JsonNode lastMessage() {
        List<JsonNode> messages = calls("sendMessage");
        if (messages.isEmpty()) {
            throw new AssertionError("nenhuma mensagem enviada");
        }
        return messages.getLast();
    }

    /** {@code callback_data} de todos os botões de uma mensagem. */
    protected static List<String> buttons(JsonNode message) {
        List<String> data = new ArrayList<>();
        JsonNode keyboard = message.path("reply_markup").path("inline_keyboard");
        for (JsonNode line : keyboard) {
            for (JsonNode button : line) {
                data.add(button.get("callback_data").asString());
            }
        }
        return data;
    }

    protected static List<String> buttonLabels(JsonNode message) {
        List<String> labels = new ArrayList<>();
        for (JsonNode line : message.path("reply_markup").path("inline_keyboard")) {
            for (JsonNode button : line) {
                labels.add(button.get("text").asString());
            }
        }
        return labels;
    }
}
