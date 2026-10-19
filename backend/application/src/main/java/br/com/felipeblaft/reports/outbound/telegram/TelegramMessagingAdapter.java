package br.com.felipeblaft.reports.outbound.telegram;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chamadas do backend à API de bots no servidor local {@code telegram-bot-api} (R-05). Depois da
 * feature 004, o n8n é o único transporte das mensagens do chat; o backend só chama {@code getFile}
 * (download do áudio, que nunca passa pelo n8n) e {@code setMyCommands} (comandos do bot). Falhas
 * são registradas sem o conteúdo das mensagens (FR-055).
 */
@Component
public class TelegramMessagingAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramMessagingAdapter.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final boolean enabled;
    private final String baseUrl;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TelegramMessagingAdapter(ApplicationProperties properties) {
        ApplicationProperties.Telegram telegram = properties.telegram();
        this.enabled = telegram.token() != null && !telegram.token().isBlank();
        this.baseUrl = telegram.apiUrl() + "/bot" + telegram.token() + "/";
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
                LOG.warn("telegram-failure method={} status={}", method, response.statusCode());
                return Optional.empty();
            }
            JsonNode no = JSON.readTree(response.body());
            return Optional.ofNullable(no.get("result"));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LOG.warn("telegram-interrupted method={}", method);
            return Optional.empty();
        } catch (Exception error) {
            LOG.warn("telegram-error method={} type={}", method, error.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
