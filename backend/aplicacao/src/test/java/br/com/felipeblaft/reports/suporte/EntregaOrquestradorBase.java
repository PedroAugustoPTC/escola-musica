package br.com.felipeblaft.reports.suporte;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.TeacherNotificationPort;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.saida.fila.EntregaAcoesCanalTarefa;
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
            "relatorios.canal-telegram.entrega-proativa=ORQUESTRADOR",
            "relatorios.canal-telegram.orquestrador-token=segredo-do-orquestrador",
            "relatorios.canal-telegram.intervalo-despacho-ms=3600000",
            "relatorios.canal-telegram.conexao-timeout-ms=500",
            "relatorios.canal-telegram.resposta-timeout-ms=1500"
        })
public abstract class EntregaOrquestradorBase extends TelegramConversaBase {

    public static final WireMockServer ORQUESTRADOR =
            new WireMockServer(wireMockConfig().dynamicPort());
    public static final String CAMINHO = "/webhook/segredo-no-caminho-123";

    static {
        ORQUESTRADOR.start();
    }

    @DynamicPropertySource
    static void orquestrador(DynamicPropertyRegistry registro) {
        registro.add(
                "relatorios.canal-telegram.orquestrador-url",
                () -> "http://localhost:" + ORQUESTRADOR.port() + CAMINHO);
    }

    @Autowired protected DeliverPendingActionsUseCase entregar;
    @Autowired protected EntregaAcoesCanalTarefa tarefa;
    @Autowired protected ChannelActionRepository acoes;
    @Autowired protected MeterRegistry metricas;
    @Autowired protected JobQueuePort fila;
    @Autowired protected TeacherNotificationPort notificacao;
    @Autowired protected RegisterLessonByAudioUseCase registrar;

    protected Teacher clara;
    protected Student matteo;
    protected Student helena;

    /** Quantas entregas já foram passadas ao executor por {@link #drenar(int)}. */
    private int entregasTratadas;

    @BeforeEach
    void prepararOrquestrador() {
        ORQUESTRADOR.resetAll();
        entregasTratadas = 0;
        aceitar();
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        helena = cenario.aluno(clara, "Helena Prado", "Violino");
    }

    // ---- orquestrador simulado

    protected void aceitar() {
        ORQUESTRADOR.resetMappings();
        ORQUESTRADOR.stubFor(post(urlPathEqualTo(CAMINHO)).willReturn(aResponse().withStatus(200)));
    }

    protected void responder(int status) {
        ORQUESTRADOR.resetMappings();
        ORQUESTRADOR.stubFor(
                post(urlPathEqualTo(CAMINHO)).willReturn(aResponse().withStatus(status)));
    }

    protected void responderComAtraso(int status, int atrasoMs) {
        ORQUESTRADOR.resetMappings();
        ORQUESTRADOR.stubFor(
                post(urlPathEqualTo(CAMINHO))
                        .willReturn(aResponse().withStatus(status).withFixedDelay(atrasoMs)));
    }

    protected List<LoggedRequest> entregas() {
        return ORQUESTRADOR.findAll(postRequestedFor(urlPathEqualTo(CAMINHO)));
    }

    /** Corpo da entrega, validado contra {@code #/$defs/LoteEntrega}. */
    protected JsonNode corpo(LoggedRequest entrega) {
        EsquemaAcoesV1.exigir("LoteEntrega", entrega.getBodyAsString());
        return JSON.readTree(entrega.getBodyAsString());
    }

    /**
     * Faz o que o n8n faria: despacha, passa ao executor cada lote entregue desde a última chamada
     * e confirma. Repete até não haver mais nada devido (ou {@code ciclos} vezes).
     */
    protected void drenar(int ciclos) throws Exception {
        for (int i = 0; i < ciclos; i++) {
            tarefa.despachar();
            List<LoggedRequest> todas = entregas();
            if (todas.size() == entregasTratadas) {
                return;
            }
            for (int k = entregasTratadas; k < todas.size(); k++) {
                executorDoOrquestrador().executar(corpo(todas.get(k)).get("acoes"));
            }
            entregasTratadas = todas.size();
        }
    }

    // ---- consultas

    protected String estado(String acaoId) {
        return jdbc.queryForObject(
                "select estado from acao_canal where id = ?", String.class, acaoId);
    }

    protected List<String> idsDasAcoes() {
        return jdbc.queryForList("select id from acao_canal order by seq", String.class);
    }

    // ---- cenário

    protected TemporaryFile audioDaWeb() throws Exception {
        Path diretorio = propriedades.audio().tempDir();
        Files.createDirectories(diretorio);
        Path arquivo = diretorio.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(arquivo, NormalizacaoAudioTeste.AUDIO_COM_FALA);
        return new TemporaryFile(arquivo, Files.size(arquivo), "audio/webm");
    }

    /** Trabalho da Web com o professor vinculado: o resultado vira um aviso proativo (1 ação). */
    protected void gerarAvisoDe(Student aluno) throws Exception {
        registrar.run(clara.id(), aluno.id(), audioDaWeb(), Channel.WEB);
        cenario.processarFila();
    }
}
