package br.com.felipeblaft.relatorios.suporte;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import br.com.felipeblaft.relatorios.casodeuso.aula.RegistrarAulaPorAudioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.canal.EntregarAcoesPendentesCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.NotificacaoProfessorPorta;
import br.com.felipeblaft.relatorios.saida.fila.EntregaAcoesCanalTarefa;
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

    @Autowired protected EntregarAcoesPendentesCasoDeUso entregar;
    @Autowired protected EntregaAcoesCanalTarefa tarefa;
    @Autowired protected AcaoCanalRepositorio acoes;
    @Autowired protected MeterRegistry metricas;
    @Autowired protected FilaTrabalhoPorta fila;
    @Autowired protected NotificacaoProfessorPorta notificacao;
    @Autowired protected RegistrarAulaPorAudioCasoDeUso registrar;

    protected Professor clara;
    protected Aluno matteo;
    protected Aluno helena;

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

    protected ArquivoTemporario audioDaWeb() throws Exception {
        Path diretorio = propriedades.audio().tempDir();
        Files.createDirectories(diretorio);
        Path arquivo = diretorio.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(arquivo, NormalizacaoAudioTeste.AUDIO_COM_FALA);
        return new ArquivoTemporario(arquivo, Files.size(arquivo), "audio/webm");
    }

    /** Trabalho da Web com o professor vinculado: o resultado vira um aviso proativo (1 ação). */
    protected void gerarAvisoDe(Aluno aluno) throws Exception {
        registrar.executar(clara.id(), aluno.id(), audioDaWeb(), Canal.WEB);
        cenario.processarFila();
    }
}
