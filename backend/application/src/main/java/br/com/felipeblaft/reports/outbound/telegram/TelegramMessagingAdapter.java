package br.com.felipeblaft.reports.outbound.telegram;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.inbound.telegram.TelegramPresenter;
import br.com.felipeblaft.reports.inbound.telegram.TelegramSender;
import br.com.felipeblaft.reports.outbound.persistence.JobQueuePostgresAdapter;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chamadas à API de bots no servidor local {@code telegram-bot-api} (R-05): {@code sendMessage}
 * (HTML, texto escapado), {@code editMessageText}, {@code answerCallbackQuery}, {@code
 * sendDocument} e {@code getFile}. Falhas são registradas sem o conteúdo das mensagens (FR-055).
 */
@Component
public class TelegramMessagingAdapter implements TelegramSender {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramMessagingAdapter.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final boolean enabled;
    private final String baseUrl;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final JobQueuePostgresAdapter queue;

    public TelegramMessagingAdapter(
            ApplicationProperties properties, JobQueuePostgresAdapter queue) {
        this.queue = queue;
        ApplicationProperties.Telegram telegram = properties.telegram();
        this.enabled = telegram.token() != null && !telegram.token().isBlank();
        this.baseUrl = telegram.apiUrl() + "/bot" + telegram.token() + "/";
    }

    @Override
    public void send(TelegramOutput output) {
        switch (output) {
            case TelegramOutput.Message m -> sendMessage(m.chatId(), m.text(), m.keyboard());
            case TelegramOutput.Edit e -> edit(e);
            case TelegramOutput.StepMessage m -> sendStep(m);
            case TelegramOutput.Document d -> sendDocument(d);
            case TelegramOutput.CallbackReply r ->
                    call("answerCallbackQuery", Map.of("callback_query_id", r.callbackId()));
        }
    }

    /**
     * Envia (ou edita no lugar) a mensagem de etapa e grava o seu id no trabalho, para que o worker
     * edite a mesma mensagem a cada etapa (sequência 5.2, passo 4).
     */
    private void sendStep(TelegramOutput.StepMessage step) {
        Optional<Long> messageId;
        if (step.editMessageId() != null) {
            edit(
                    new TelegramOutput.Edit(
                            step.chatId(),
                            step.editMessageId(),
                            step.text(),
                            TelegramOutput.Keyboard.NONE));
            messageId = Optional.of(step.editMessageId());
        } else {
            messageId = sendMessage(step.chatId(), step.text(), TelegramOutput.Keyboard.NONE);
        }
        messageId.ifPresent(id -> queue.registerStepMessage(step.jobId(), id));
    }

    /** Envia uma mensagem nova; devolve o id da última parte enviada, se a API respondeu. */
    public Optional<Long> sendMessage(long chatId, String text, TelegramOutput.Keyboard keyboard) {
        Optional<Long> lastId = Optional.empty();
        List<String> parts = TelegramPresenter.splitText(text);
        for (int i = 0; i < parts.size(); i++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId);
            body.put("text", parts.get(i));
            body.put("parse_mode", "HTML");
            body.put("link_preview_options", Map.of("is_disabled", true));
            if (i == parts.size() - 1 && keyboard != null && !keyboard.empty()) {
                body.put("reply_markup", keyboardJson(keyboard));
            }
            lastId =
                    call("sendMessage", body)
                            .map(result -> result.path("message_id"))
                            .filter(JsonNode::isNumber)
                            .map(JsonNode::asLong);
        }
        return lastId;
    }

    public void edit(TelegramOutput.Edit edit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", edit.chatId());
        body.put("message_id", edit.messageId());
        body.put("text", edit.text());
        body.put("parse_mode", "HTML");
        body.put(
                "reply_markup",
                keyboardJson(
                        edit.keyboard() == null ? TelegramOutput.Keyboard.NONE : edit.keyboard()));
        call("editMessageText", body);
    }

    static Map<String, Object> keyboardJson(TelegramOutput.Keyboard keyboard) {
        return Map.of(
                "inline_keyboard",
                keyboard.rows().stream()
                        .map(
                                line ->
                                        line.stream()
                                                .map(
                                                        button ->
                                                                Map.of(
                                                                        "text",
                                                                        button.label(),
                                                                        "callback_data",
                                                                        button.data()))
                                                .toList())
                        .toList());
    }

    private void sendDocument(TelegramOutput.Document document) {
        if (!enabled) {
            return;
        }
        String boundary = "----relatorios" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "chat_id", Long.toString(document.chatId()));
        if (document.caption() != null) {
            field(body, boundary, "caption", document.caption());
            field(body, boundary, "parse_mode", "HTML");
        }
        write(
                body,
                "--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\"document\"; filename=\""
                        + document.fileName()
                        + "\"\r\nContent-Type: application/pdf\r\n\r\n");
        body.writeBytes(document.content());
        write(body, "\r\n--" + boundary + "--\r\n");
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(baseUrl + "sendDocument"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                        .build();
        run("sendDocument", request);
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

    /** Chama um método da API; devolve o campo {@code result} em caso de sucesso. */
    public Optional<JsonNode> call(String method, Map<String, ?> parameters) {
        if (!enabled) {
            return Optional.empty();
        }
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(baseUrl + method))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        JSON.writeValueAsString(parameters)))
                        .build();
        return run(method, request);
    }

    private Optional<JsonNode> run(String method, HttpRequest request) {
        try {
            HttpResponse<String> response =
                    client.send(
                            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                LOG.warn("telegram-falha metodo={} status={}", method, response.statusCode());
                return Optional.empty();
            }
            JsonNode no = JSON.readTree(response.body());
            return Optional.ofNullable(no.get("result"));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LOG.warn("telegram-interrompido metodo={}", method);
            return Optional.empty();
        } catch (Exception error) {
            LOG.warn("telegram-erro metodo={} tipo={}", method, error.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
