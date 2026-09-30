package br.com.felipeblaft.relatorios.suporte;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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
 * Base dos testes de conversa do Telegram (contracts/telegram-conversa.md §7): envia updates JSON ao
 * webhook com o segredo e verifica as chamadas feitas à API de bots simulada no WireMock.
 */
@AutoConfigureMockMvc
public abstract class TelegramConversaBase extends IntegracaoBase {

    public static final String SEGREDO = "segredo-webhook-teste";
    public static final String TOKEN = "123456:TESTE";
    protected static final long USUARIO_CLARA = 7001L;
    protected static final long USUARIO_BRUNO = 7002L;
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    protected static final WireMockServer API_BOTS = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        API_BOTS.start();
    }

    private static final AtomicLong UPDATES = new AtomicLong(1000);
    private static final AtomicLong MENSAGENS = new AtomicLong(1);

    @Autowired protected MockMvc mvc;

    @Autowired protected CenarioIT cenario;

    @DynamicPropertySource
    static void apiDeBots(DynamicPropertyRegistry registro) {
        registro.add("relatorios.telegram.api-url", API_BOTS::baseUrl);
        registro.add("relatorios.telegram.token", () -> TOKEN);
        registro.add("relatorios.telegram.segredo-webhook", () -> SEGREDO);
    }

    @BeforeEach
    void limparApiDeBots() {
        API_BOTS.resetAll();
        API_BOTS.stubFor(
                post(anyUrl())
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":{\"message_id\":1}}")));
    }

    // ---------------------------------------------------------------- envio de updates

    protected long proximoUpdate() {
        return UPDATES.incrementAndGet();
    }

    protected void enviarUpdate(String json) throws Exception {
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/telegram/webhook")
                                .header("X-Telegram-Bot-Api-Secret-Token", SEGREDO)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isOk());
    }

    protected static String mensagemJson(long updateId, long usuario, String campoEConteudo) {
        return mensagemJson(updateId, usuario, "private", campoEConteudo);
    }

    protected static String mensagemJson(long updateId, long usuario, String tipoChat, String campoEConteudo) {
        return """
                {"update_id": %d, "message": {"message_id": %d, "date": 1,
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "chat": {"id": %d, "type": "%s"}, %s}}
                """
                .formatted(updateId, MENSAGENS.incrementAndGet(), usuario, usuario, tipoChat, campoEConteudo);
    }

    protected void enviarTexto(long usuario, String texto) throws Exception {
        enviarUpdate(mensagemJson(proximoUpdate(), usuario, "\"text\": " + JSON.writeValueAsString(texto)));
    }

    protected void enviarCallback(long usuario, String dados) throws Exception {
        enviarUpdate(
                """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": %s,
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(
                                proximoUpdate(),
                                MENSAGENS.incrementAndGet(),
                                JSON.writeValueAsString(dados),
                                usuario,
                                usuario));
    }

    protected void enviarVoz(long usuario, String fileId, long tamanho) throws Exception {
        enviarUpdate(
                mensagemJson(
                        proximoUpdate(),
                        usuario,
                        "\"voice\": {\"file_id\": \"%s\", \"file_unique_id\": \"u-%s\", \"mime_type\": \"audio/ogg\", \"file_size\": %d, \"duration\": 30}"
                                .formatted(fileId, fileId, tamanho)));
    }

    /** Vincula a conta de teste com o código (sequência 5.1) e limpa as chamadas registradas. */
    protected void vincular(long usuario, String codigo) throws Exception {
        enviarTexto(usuario, codigo);
        API_BOTS.resetRequests();
    }

    // ---------------------------------------------------------------- verificações

    protected static List<JsonNode> chamadas(String metodo) {
        List<JsonNode> corpos = new ArrayList<>();
        for (LoggedRequest requisicao :
                API_BOTS.findAll(postRequestedFor(urlPathMatching("/bot" + TOKEN.replace(":", "\\:") + "/" + metodo)))) {
            if (requisicao.getHeader("Content-Type") != null
                    && requisicao.getHeader("Content-Type").startsWith("application/json")) {
                corpos.add(JSON.readTree(requisicao.getBodyAsString()));
            } else {
                corpos.add(JSON.createObjectNode().put("multipart", requisicao.getBodyAsString().length()));
            }
        }
        return corpos;
    }

    protected static List<String> textosEnviados() {
        return chamadas("sendMessage").stream().map(m -> m.get("text").asString()).toList();
    }

    protected static JsonNode ultimaMensagem() {
        List<JsonNode> mensagens = chamadas("sendMessage");
        if (mensagens.isEmpty()) {
            throw new AssertionError("nenhuma mensagem enviada");
        }
        return mensagens.getLast();
    }

    /** {@code callback_data} de todos os botões de uma mensagem. */
    protected static List<String> botoes(JsonNode mensagem) {
        List<String> dados = new ArrayList<>();
        JsonNode teclado = mensagem.path("reply_markup").path("inline_keyboard");
        for (JsonNode linha : teclado) {
            for (JsonNode botao : linha) {
                dados.add(botao.get("callback_data").asString());
            }
        }
        return dados;
    }

    protected static List<String> rotulosBotoes(JsonNode mensagem) {
        List<String> rotulos = new ArrayList<>();
        for (JsonNode linha : mensagem.path("reply_markup").path("inline_keyboard")) {
            for (JsonNode botao : linha) {
                rotulos.add(botao.get("text").asString());
            }
        }
        return rotulos;
    }
}
