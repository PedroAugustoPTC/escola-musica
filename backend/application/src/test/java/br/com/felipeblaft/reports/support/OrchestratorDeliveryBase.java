package br.com.felipeblaft.reports.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.outbound.queue.ChannelActionsDeliveryTask;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.TeacherNotificationPort;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.usecase.channel.DeliverPendingActionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Base dos testes que controlam a entrega proativa pelo orquestrador: o WireMock da base faz o
 * papel do webhook do n8n e o executor simulado faz o papel dos seus workflows. Diferente da base
 * de conversa, a entrega não acontece na publicação ({@code test.telegram.drain-proactive=false}):
 * o teste chama os ciclos ({@link #drain(int)}), com o tempo do relógio ajustável. Todas as
 * subclasses compartilham o mesmo contexto Spring.
 *
 * <p>Modo {@code n8n} (feature 004, {@code telegram.transport=n8n}): os testes que programam a
 * resposta do orquestrador ({@link #accept()}, {@link #answer(int)}, {@link #replyWithDelay(int,
 * int)}) testam o backend e continuam com o WireMock. Já {@link #drain(int)} faz o que o n8n faria
 * <em>de verdade</em>: o WireMock vira proxy do {@code channel-delivery} real, que executa e
 * confirma antes de responder, e o executor em memória não é usado. Com {@code
 * -Dtelegram.n8n.duplicates=true} cada lote entregue é reentregue ao n8n, que não pode repetir
 * nada.
 */
@TestPropertySource(properties = "test.telegram.drain-proactive=false")
public abstract class OrchestratorDeliveryBase extends TelegramConversationBase {

    private static final boolean N8N = "n8n".equals(TRANSPORT);

    @Autowired protected DeliverPendingActionsUseCase deliver;
    @Autowired protected ChannelActionsDeliveryTask task;
    @Autowired protected ChannelActionRepository actions;
    @Autowired protected MeterRegistry metrics;
    @Autowired protected JobQueuePort queue;
    @Autowired protected TeacherNotificationPort notification;
    @Autowired protected RegisterLessonByAudioUseCase register;

    protected Teacher clara;
    protected Student matteo;
    protected Student helena;

    /** Quantas entregas já foram tratadas por {@link #drain(int)}. */
    private int handledDeliveries;

    @BeforeEach
    void prepareOrchestrator() {
        ORCHESTRATOR.resetAll();
        handledDeliveries = 0;
        accept();
        clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        helena = scenario.student(clara, "Helena Prado", "Violino");
    }

    /**
     * O vínculo responde ao update pela API interna e deixa as ações da resposta na outbox; estes
     * testes só querem as ações proativas que eles mesmos provocam.
     */
    @Override
    protected void link(long user, String code) throws Exception {
        super.link(user, code);
        jdbc.update("delete from channel_action");
    }

    /**
     * Volta ao estado inicial no meio de um teste (banco, API de bots e orquestrador simulados).
     */
    protected void resetScenario() {
        truncateAll();
        resetBotApi();
        prepareOrchestrator();
    }

    // ---- orquestrador simulado

    protected void accept() {
        ORCHESTRATOR.resetMappings();
        ORCHESTRATOR.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200)));
    }

    /** Modo n8n: o WireMock passa a encaminhar as entregas ao {@code channel-delivery} real. */
    private void routeDeliveriesToN8n() {
        ORCHESTRATOR.resetMappings();
        ORCHESTRATOR.stubFor(
                post(urlPathEqualTo(PATH))
                        .willReturn(aResponse().proxiedFrom(N8nTestSupport.N8N_URL)));
    }

    protected void answer(int status) {
        ORCHESTRATOR.resetMappings();
        ORCHESTRATOR.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(status)));
    }

    protected void replyWithDelay(int status, int delayMs) {
        ORCHESTRATOR.resetMappings();
        ORCHESTRATOR.stubFor(
                post(urlPathEqualTo(PATH))
                        .willReturn(aResponse().withStatus(status).withFixedDelay(delayMs)));
    }

    protected List<LoggedRequest> deliveries() {
        return ORCHESTRATOR.findAll(postRequestedFor(urlPathEqualTo(PATH)));
    }

    /** Corpo da entrega, validado contra {@code #/$defs/LoteEntrega}. */
    protected JsonNode body(LoggedRequest delivery) {
        ActionsSchemaV1.require("DeliveryBatch", delivery.getBodyAsString());
        return JSON.readTree(delivery.getBodyAsString());
    }

    /**
     * Faz o que o n8n faria: despacha, passa ao executor cada lote entregue desde a última chamada
     * e confirma. Repete até não haver mais nada devido (ou {@code ciclos} vezes).
     */
    protected void drain(int cycles) throws Exception {
        if (N8N) {
            routeDeliveriesToN8n();
        }
        try {
            drainCycles(cycles);
        } finally {
            if (N8N) {
                accept();
            }
        }
    }

    private void drainCycles(int cycles) throws Exception {
        for (int i = 0; i < cycles; i++) {
            task.dispatch();
            List<LoggedRequest> all = deliveries();
            if (all.size() == handledDeliveries) {
                return;
            }
            for (int k = handledDeliveries; k < all.size(); k++) {
                if (N8N) {
                    // o n8n real já executou e confirmou antes de responder; a duplicidade o
                    // reentrega
                    if (n8nDuplicates) {
                        redeliver(all.get(k));
                    }
                } else {
                    orchestratorExecutor().run(body(all.get(k)).get("actions"));
                }
            }
            handledDeliveries = all.size();
        }
    }

    /** Entrega de novo ao n8n o mesmo lote, com os mesmos cabeçalhos (modo de duplicidade). */
    private void redeliver(LoggedRequest delivery) {
        String token = delivery.getHeader("X-Channel-Delivery-Token");
        String key = delivery.getHeader("Idempotency-Key");
        N8nTestSupport.Reply reply =
                N8nTestSupport.post(
                        N8nTestSupport.N8N_URL + "/webhook/channel-delivery",
                        java.util.Map.of("X-Channel-Delivery-Token", token, "Idempotency-Key", key),
                        "application/json",
                        delivery.getBody());
        if (reply.status() != 200) {
            throw new AssertionError("a reentrega do lote foi recusada com " + reply.status());
        }
    }

    // ---- consultas

    protected String state(String actionId) {
        return jdbc.queryForObject(
                "select state from channel_action where id = ?", String.class, actionId);
    }

    protected List<String> actionIds() {
        return jdbc.queryForList("select id from channel_action order by seq", String.class);
    }

    // ---- cenário

    protected TemporaryFile webAudio() throws Exception {
        Path directory = properties.audio().tempDir();
        Files.createDirectories(directory);
        Path file = directory.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(file, AudioNormalizationTest.AUDIO_WITH_SPEECH);
        return new TemporaryFile(file, Files.size(file), "audio/webm");
    }

    /** Trabalho da Web com o professor vinculado: o resultado vira um aviso proativo (1 ação). */
    protected void generateNoticeOf(Student student) throws Exception {
        register.run(clara.id(), student.id(), webAudio(), Channel.WEB);
        scenario.processQueue();
    }
}
