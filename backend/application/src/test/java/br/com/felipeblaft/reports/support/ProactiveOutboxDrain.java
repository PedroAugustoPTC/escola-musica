package br.com.felipeblaft.reports.support;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import br.com.felipeblaft.reports.outbound.queue.ChannelActionsDeliveryTask;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Faz, nos testes de conversa, o que o despachante e o n8n fazem em produção com as mensagens
 * proativas: entrega à outbox ao orquestrador simulado e executa as ações na API de bots simulada
 * (executor em memória) ou, no modo {@code n8n}, deixa o n8n real executá-las. É chamado <strong>na
 * publicação</strong> ({@link DrainingPublicationAdapter}), então depois de cada notificação o chat
 * de teste já está como o professor o veria. Só existe com {@code
 * test.telegram.drain-proactive=true}.
 */
@Component
@ConditionalOnProperty(name = "test.telegram.drain-proactive", havingValue = "true")
public class ProactiveOutboxDrain {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_CYCLES = 10;
    private static final boolean N8N = "n8n".equals(TelegramConversationBase.TRANSPORT);

    private final ChannelActionsDeliveryTask task;
    private final MockMvc mvc;
    private final Set<UUID> handled = new HashSet<>();

    @Autowired
    public ProactiveOutboxDrain(ChannelActionsDeliveryTask task, MockMvc mvc) {
        this.task = task;
        this.mvc = mvc;
    }

    /** Despacha e executa até não restar lote devido (ou {@value #MAX_CYCLES} ciclos). */
    public synchronized void drain() {
        try {
            for (int cycle = 0; cycle < MAX_CYCLES; cycle++) {
                task.dispatch();
                List<LoggedRequest> fresh =
                        TelegramConversationBase.ORCHESTRATOR
                                .findAll(
                                        postRequestedFor(
                                                urlPathEqualTo(TelegramConversationBase.PATH)))
                                .stream()
                                .filter(request -> !handled.contains(request.getId()))
                                .toList();
                if (fresh.isEmpty()) {
                    return;
                }
                for (LoggedRequest delivery : fresh) {
                    handled.add(delivery.getId());
                    handle(delivery);
                }
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("a entrega proativa de teste falhou", error);
        }
    }

    private void handle(LoggedRequest delivery) throws Exception {
        if (N8N) {
            // o n8n real já executou e confirmou antes de responder; a duplicidade o reentrega
            if (TelegramConversationBase.n8nDuplicates()) {
                redeliver(delivery);
            }
            return;
        }
        ActionsSchemaV1.require("DeliveryBatch", delivery.getBodyAsString());
        JsonNode actions = JSON.readTree(delivery.getBodyAsString()).get("actions");
        new SimulatedActionsExecutorTest(
                        new InternalClientMockMvc(mvc, TelegramConversationBase.TOKEN_SERVICE),
                        TelegramConversationBase.API_BOTS.baseUrl()
                                + "/bot"
                                + TelegramConversationBase.TOKEN)
                .run(actions);
    }

    private void redeliver(LoggedRequest delivery) {
        N8nTestSupport.Reply reply =
                N8nTestSupport.post(
                        N8nTestSupport.N8N_URL + "/webhook/channel-delivery",
                        Map.of(
                                "X-Channel-Delivery-Token",
                                delivery.getHeader("X-Channel-Delivery-Token"),
                                "Idempotency-Key",
                                delivery.getHeader("Idempotency-Key")),
                        "application/json",
                        delivery.getBody());
        if (reply.status() != 200) {
            throw new AssertionError("a reentrega do lote foi recusada com " + reply.status());
        }
    }
}
