package br.com.felipeblaft.reports.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

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
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Base dos testes com a entrega proativa ligada ao orquestrador ({@code ORQUESTRADOR}): um WireMock
 * faz o papel do webhook do n8n e o executor simulado faz o papel dos seus workflows. O despachante
 * agendado fica desligado (intervalo de 1 h): o teste chama os ciclos, com o tempo do relógio
 * ajustável. Todas as subclasses compartilham o mesmo contexto Spring.
 */
@TestPropertySource(
        properties = {
            "reports.telegram-channel.proactive-delivery=ORCHESTRATOR",
            "reports.telegram-channel.orchestrator-token=orchestrator-secret",
            "reports.telegram-channel.dispatch-interval-ms=3600000",
            "reports.telegram-channel.connection-timeout-ms=500",
            "reports.telegram-channel.response-timeout-ms=1500"
        })
public abstract class OrchestratorDeliveryBase extends TelegramConversationBase {

    public static final WireMockServer ORCHESTRATOR =
            new WireMockServer(wireMockConfig().dynamicPort());
    public static final String PATH = "/webhook/secret-in-path-123";

    static {
        ORCHESTRATOR.start();
    }

    @DynamicPropertySource
    static void orchestrator(DynamicPropertyRegistry registration) {
        registration.add(
                "reports.telegram-channel.orchestrator-url",
                () -> "http://localhost:" + ORCHESTRATOR.port() + PATH);
    }

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

    /** Quantas entregas já foram passadas ao executor por {@link #drain(int)}. */
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

    // ---- orquestrador simulado

    protected void accept() {
        ORCHESTRATOR.resetMappings();
        ORCHESTRATOR.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200)));
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
        for (int i = 0; i < cycles; i++) {
            task.dispatch();
            List<LoggedRequest> all = deliveries();
            if (all.size() == handledDeliveries) {
                return;
            }
            for (int k = handledDeliveries; k < all.size(); k++) {
                orchestratorExecutor().run(body(all.get(k)).get("actions"));
            }
            handledDeliveries = all.size();
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
