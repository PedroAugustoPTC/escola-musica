package br.com.felipeblaft.reports.outbound.telegram;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.inbound.telegram.channel.ChannelActionMapper;
import br.com.felipeblaft.reports.inbound.telegram.channel.DeliveryBatchDto;
import br.com.felipeblaft.reports.port.ChannelOrchestratorPort;
import br.com.felipeblaft.reports.port.DeliveryBatch;
import br.com.felipeblaft.reports.port.DeliveryResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Entrega de lotes proativos ao webhook do orquestrador (n8n) pelo cliente HTTP do JDK: timeouts de
 * conexão e de resposta configuráveis, sem seguir redirecionamentos, {@code Idempotency-Key} igual
 * ao {@code loteId} e o segredo do destino em {@code X-Canal-Entrega-Token}. Qualquer {@code 2xx} é
 * aceite; qualquer outra coisa (timeout, falha de conexão, 4xx, 5xx) é recusa, sem encerrar as
 * tentativas: quem decide reenviar é o núcleo. O nome do fornecedor só existe nesta camada.
 *
 * <p>Nenhum endereço vem do conteúdo das ações. Em log vão só o {@code loteId}, o status HTTP, o
 * código de situação e o tipo da exceção: nunca corpo, mensagem de exceção, URL (um webhook do n8n
 * pode trazer segredo no caminho) nem token.
 */
@Component
public class N8nOrchestratorAdapter implements ChannelOrchestratorPort {

    public static final String HEADER_TOKEN = "X-Channel-Delivery-Token";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private static final Logger LOG = LoggerFactory.getLogger(N8nOrchestratorAdapter.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final URI destination;
    private final String token;
    private final Duration responseTimeout;
    private final HttpClient client;

    public N8nOrchestratorAdapter(ApplicationProperties properties) {
        ApplicationProperties.TelegramChannel channel = properties.telegramChannel();
        this.destination =
                channel.configuredOrchestrator() ? URI.create(channel.orchestratorUrl()) : null;
        this.token = channel.orchestratorToken();
        this.responseTimeout = Duration.ofMillis(channel.responseTimeoutMs());
        this.client =
                HttpClient.newBuilder()
                        // HTTP/1.1 fixo: o padrão (HTTP/2) tenta o upgrade h2c em texto claro, que o
                        // webhook do n8n nunca responde — a chamada trava até o timeout de resposta
                        // (reproduzido com curl --http2 puro, sem nenhum código deste projeto).
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofMillis(channel.connectionTimeoutMs()))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    @Override
    public DeliveryResult deliver(DeliveryBatch batch) {
        if (destination == null) {
            LOG.warn("channel-delivery-without-destination batchId={}", batch.batchId());
            return DeliveryResult.refused("NO_DESTINATION");
        }
        HttpRequest.Builder request =
                HttpRequest.newBuilder(destination)
                        .timeout(responseTimeout)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header(IDEMPOTENCY_HEADER, batch.batchId())
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        JSON.writeValueAsString(body(batch)),
                                        StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) {
            request.header(HEADER_TOKEN, token);
        }
        try {
            HttpResponse<Void> response =
                    client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status / 100 == 2) {
                LOG.info("channel-delivery-accepted batchId={} status={}", batch.batchId(), status);
                return DeliveryResult.accept();
            }
            LOG.warn("channel-delivery-refused batchId={} status={}", batch.batchId(), status);
            return DeliveryResult.refused("HTTP_" + status);
        } catch (HttpTimeoutException timeout) { // inclui o timeout de conexão
            LOG.warn("channel-delivery-refused batchId={} outcome=TIMEOUT", batch.batchId());
            return DeliveryResult.refused("TIMEOUT");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOG.warn("channel-delivery-refused batchId={} outcome=INTERRUPTED", batch.batchId());
            return DeliveryResult.refused("INTERRUPTED");
        } catch (IOException | RuntimeException error) {
            LOG.warn(
                    "channel-delivery-refused batchId={} outcome=CONNECTION type={}",
                    batch.batchId(),
                    error.getClass().getSimpleName());
            return DeliveryResult.refused("CONNECTION");
        }
    }

    /** Monta o {@code LoteEntrega} v1: as ações com {@code acaoId} e, nas etapas, o id a editar. */
    static DeliveryBatchDto body(DeliveryBatch batch) {
        List<JsonNode> actions = new ArrayList<>();
        for (DeliveryBatch.Action action : batch.actions()) {
            ObjectNode no = ChannelActionMapper.withActionId(action.actionId(), action.payload());
            if (action.editMessageId() != null) {
                // o id conhecido do trabalho no momento do despacho vale mais que o da publicação
                no.put("editMessageId", action.editMessageId());
            }
            actions.add(no);
        }
        return new DeliveryBatchDto(
                "v1",
                batch.batchId(),
                batch.origin().name(),
                batch.createdAt().toString(),
                batch.attempt(),
                actions);
    }
}
