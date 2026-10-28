package br.com.felipeblaft.reports.support;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Especificação executável do workflow do orquestrador (feature 004): faz o que o n8n fará, e só
 * isso. Percorre as ações em ordem <strong>sem parar na primeira falha</strong>, traduz cada uma
 * para a API de bots (aqui, o WireMock) conforme a tabela de contracts/canal-telegram-interno.md §4
 * e informa o resultado por {@code POST /confirmacoes}. Não decide nada: não compensa, não reenvia,
 * não interpreta texto. Sem estado: a idempotência vem de {@code GET /acoes/{id}}.
 */
public class SimulatedActionsExecutorTest {

    /**
     * Chamadas à API interna do backend (o teste usa MockMvc; a verificação do executor, um dublê).
     */
    public interface InternalClient {

        record Response(int status, byte[] body) {

            public String text() {
                return new String(body, StandardCharsets.UTF_8);
            }
        }

        Response get(String path) throws Exception;

        Response post(String path, String bodyJson) throws Exception;
    }

    /** Resultado informado ao backend para uma ação. */
    public record Result(String actionId, String status, String reasonCode, Long messageId) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BASE = "/internal/v1/channel/telegram";

    private final InternalClient internal;
    private final String urlApiBots;
    private final HttpClient http = HttpClient.newHttpClient();

    /** Resultados já informados por este executor, na ordem (para asserções dos testes). */
    public final List<Result> informed = new ArrayList<>();

    public SimulatedActionsExecutorTest(InternalClient internal, String urlApiBotsComToken) {
        this.internal = internal;
        this.urlApiBots =
                urlApiBotsComToken.endsWith("/") ? urlApiBotsComToken : urlApiBotsComToken + "/";
    }

    /** Executa as ações da lista e confirma os resultados num único lote. */
    public void run(JsonNode actions) throws Exception {
        List<Result> results = new ArrayList<>();
        for (JsonNode action : actions) {
            String actionId = action.path("actionId").asString();
            if (alreadyHandled(actionId)) {
                continue;
            }
            results.add(runOne(actionId, action));
        }
        confirm(results);
    }

    // ------------------------------------------------------------------ idempotência

    /** O receptor não guarda estado: consulta o backend e pula o que já está tratado. */
    private boolean alreadyHandled(String actionId) throws Exception {
        InternalClient.Response response = internal.get(BASE + "/actions/" + actionId);
        if (response.status() != 200) {
            return false;
        }
        String state = JSON.readTree(response.body()).path("state").asString();
        return state.equals("EXECUTED") || state.equals("FAILED");
    }

    // ------------------------------------------------------------------ tradução ação -> API de
    // bots

    private Result runOne(String actionId, JsonNode action) throws Exception {
        String type = action.path("type").asString();
        return switch (type) {
            case "MESSAGE" -> sendMessage(actionId, action);
            case "EDIT" -> edit(actionId, action, action.path("messageId").asLong());
            case "DOCUMENT" -> sendDocument(actionId, action);
            case "CALLBACK_REPLY" ->
                    resultOf(
                            actionId,
                            callJson(
                                    "answerCallbackQuery",
                                    JSON.createObjectNode()
                                            .put(
                                                    "callback_query_id",
                                                    action.path("callbackId").asString())),
                            null);
            case "STEP_MESSAGE" -> step(actionId, action);
            case "DELETE_MESSAGE" -> deleteMessage(actionId, action);
            // tipo novo: pula, informa e segue com as demais (FR-003a)
            default -> new Result(actionId, "FAILED", "ACTION_NOT_SUPPORTED", null);
        };
    }

    private Result sendMessage(String actionId, JsonNode action) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("chat_id", action.path("chatId").asLong());
        body.put("text", action.path("text").asString());
        body.put("parse_mode", "HTML");
        body.set("link_preview_options", JSON.createObjectNode().put("is_disabled", true));
        JsonNode keyboard = action.path("keyboard");
        if (keyboard.path("rows").size() > 0) {
            body.set("reply_markup", keyboardTelegram(keyboard));
        }
        return resultOf(actionId, callJson("sendMessage", body), null);
    }

    private Result edit(String actionId, JsonNode action, long messageId) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("chat_id", action.path("chatId").asLong());
        body.put("message_id", messageId);
        body.put("text", action.path("text").asString());
        body.put("parse_mode", "HTML");
        // teclado vazio remove os botões
        body.set("reply_markup", keyboardTelegram(action.path("keyboard")));
        return resultOf(actionId, callJson("editMessageText", body), messageId);
    }

    /** Apaga a mensagem do chat (FR-036); falha do Telegram vira MESSAGE_NOT_DELETABLE. */
    private Result deleteMessage(String actionId, JsonNode action) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("chat_id", action.path("chatId").asLong());
        body.put("message_id", action.path("messageId").asLong());
        HttpResponse<String> response = callJson("deleteMessage", body);
        return response.statusCode() / 100 == 2
                ? new Result(actionId, "EXECUTED", null, null)
                : new Result(actionId, "FAILED", "MESSAGE_NOT_DELETABLE", null);
    }

    private Result step(String actionId, JsonNode action) throws Exception {
        if (action.hasNonNull("editMessageId")) {
            return edit(actionId, action, action.path("editMessageId").asLong());
        }
        return sendMessage(actionId, action);
    }

    /** Uma única tentativa de GET; 404 ou 5xx viram DOCUMENTO_INDISPONIVEL, sem repetir. */
    private Result sendDocument(String actionId, JsonNode action) throws Exception {
        InternalClient.Response pdf =
                internal.get(BASE + "/documents/" + action.path("documentId").asString());
        if (pdf.status() != 200) {
            return new Result(actionId, "FAILED", "DOCUMENT_UNAVAILABLE", null);
        }
        String boundary = "----executor" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "chat_id", Long.toString(action.path("chatId").asLong()));
        if (action.hasNonNull("caption")) {
            field(body, boundary, "caption", action.path("caption").asString());
            field(body, boundary, "parse_mode", "HTML");
        }
        write(
                body,
                "--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\"document\"; filename=\""
                        + action.path("fileName").asString()
                        + "\"\r\nContent-Type: "
                        + action.path("contentType").asString("application/pdf")
                        + "\r\n\r\n");
        body.writeBytes(pdf.body());
        write(body, "\r\n--" + boundary + "--\r\n");
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(urlApiBots + "sendDocument"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                        .build();
        return resultOf(actionId, http.send(request, HttpResponse.BodyHandlers.ofString()), null);
    }

    private HttpResponse<String> callJson(String method, ObjectNode body) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(urlApiBots + method))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static ObjectNode keyboardTelegram(JsonNode keyboard) {
        ArrayNode rows = JSON.createArrayNode();
        for (JsonNode line : keyboard.path("rows")) {
            ArrayNode buttons = JSON.createArrayNode();
            for (JsonNode button : line) {
                buttons.add(
                        JSON.createObjectNode()
                                .put("text", button.path("label").asString())
                                .put("callback_data", button.path("data").asString()));
            }
            rows.add(buttons);
        }
        ObjectNode no = JSON.createObjectNode();
        no.set("inline_keyboard", rows);
        return no;
    }

    // ------------------------------------------------------------------ erro do Telegram ->
    // resultado

    /**
     * @param mensagemIdConhecido id já conhecido (edição); se nulo, lido de {@code
     *     result.message_id}
     */
    private Result resultOf(String actionId, HttpResponse<String> response, Long knownMessageId) {
        int status = response.statusCode();
        String description = descriptionOf(response.body());
        if (status / 100 == 2) {
            Long messageId = knownMessageId;
            if (messageId == null) {
                JsonNode id = readResult(response.body()).path("message_id");
                messageId = id.isNumber() ? id.asLong() : null;
            }
            return new Result(actionId, "EXECUTED", null, messageId);
        }
        String code = classify(status, description);
        return code == null
                ? new Result(actionId, "EXECUTED", null, knownMessageId)
                : new Result(actionId, "FAILED", code, null);
    }

    /** Tabela "erro do Telegram -> situação" (contracts/canal-telegram-interno.md §4). */
    public static String classify(int status, String description) {
        String text = description == null ? "" : description.toLowerCase();
        if (text.contains("message is not modified")) {
            return null; // execução bem-sucedida: nada mudou
        }
        if (text.contains("message to edit not found")
                || text.contains("message can't be edited")
                || text.contains("message_id_invalid")) {
            return "MESSAGE_NOT_EDITABLE";
        }
        if (status == 403 || text.contains("chat not found")) {
            return "CHAT_UNAVAILABLE";
        }
        if (status == 429) {
            return "RATE_LIMITED";
        }
        return "TELEGRAM_ERROR";
    }

    private static String descriptionOf(String body) {
        try {
            return JSON.readTree(body).path("description").asString("");
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    private static JsonNode readResult(String body) {
        try {
            return JSON.readTree(body).path("result");
        } catch (RuntimeException unreadable) {
            return JSON.createObjectNode();
        }
    }

    // ------------------------------------------------------------------ confirmação

    private void confirm(List<Result> results) throws Exception {
        if (results.isEmpty()) {
            return;
        }
        ArrayNode items = JSON.createArrayNode();
        for (Result result : results) {
            informed.add(result);
            ObjectNode item =
                    JSON.createObjectNode()
                            .put("actionId", result.actionId())
                            .put("outcome", result.status());
            if (result.reasonCode() != null) {
                item.put("reasonCode", result.reasonCode());
            }
            if (result.messageId() != null) {
                item.put("messageId", result.messageId());
            }
            items.add(item);
        }
        ObjectNode body = JSON.createObjectNode().put("version", "v1");
        body.set("results", items);
        InternalClient.Response response = internal.post(BASE + "/confirmations", body.toString());
        if (response.status() != 200) {
            throw new AssertionError(
                    "confirmação recusada: " + response.status() + " " + response.text());
        }
        ActionsSchemaV1.require("ConfirmationResponse", response.text());
    }

    private static void field(
            ByteArrayOutputStream body, String boundary, String name, String value) {
        write(
                body,
                "--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\""
                        + name
                        + "\"\r\n\r\n"
                        + value
                        + "\r\n");
    }

    private static void write(ByteArrayOutputStream output, String text) {
        output.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
