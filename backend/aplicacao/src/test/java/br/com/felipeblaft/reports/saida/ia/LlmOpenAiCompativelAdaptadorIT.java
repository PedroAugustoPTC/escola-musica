package br.com.felipeblaft.reports.saida.ia;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.port.AiLessonReportOutput;
import br.com.felipeblaft.reports.port.InvalidAiOutputException;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.ResponseLlm;
import br.com.felipeblaft.reports.port.TaskInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Adaptador do LLM contra um servidor compatível com OpenAI simulado no WireMock (R-08, R-09). */
class LlmOpenAiCompativelAdaptadorIT {

    private static final WireMockServer SERVIDOR =
            new WireMockServer(wireMockConfig().dynamicPort());
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CAMINHO = "/v1/chat/completions";

    private static final String VALIDO =
            """
            {"conteudosTrabalhados":["Escala de Dó maior"],"evolucao":"Boa evolução.",
             "dificuldades":[],"atividadesPropostas":["Estudar a escala"],"observacoes":"",
             "perguntasPendentes":[],"campoInventado":"descartar"}
            """;

    private LlmOpenAiCompativelAdaptador adaptador;

    @BeforeAll
    static void iniciar() {
        SERVIDOR.start();
    }

    @AfterAll
    static void parar() {
        SERVIDOR.stop();
    }

    @BeforeEach
    void preparar() {
        SERVIDOR.resetAll();
        PropriedadesAplicacao.Llm configuracao =
                new PropriedadesAplicacao.Llm(
                        SERVIDOR.baseUrl() + "/v1",
                        "modelo-teste",
                        "chave-teste",
                        new BigDecimal("2"),
                        new BigDecimal("8"));
        adaptador =
                new LlmOpenAiCompativelAdaptador(
                        configuracao,
                        new ClienteHttpIa(
                                HttpClient.newHttpClient(),
                                Duration.ofMillis(10),
                                Clock.systemUTC()));
    }

    private static String resposta(String conteudo) {
        return JSON.writeValueAsString(
                java.util.Map.of(
                        "choices",
                                java.util.List.of(
                                        java.util.Map.of(
                                                "message", java.util.Map.of("content", conteudo))),
                        "usage",
                                java.util.Map.of("prompt_tokens", 1000, "completion_tokens", 500)));
    }

    private ResponseLlm<AiLessonReportOutput> estruturar() {
        return adaptador.generateJson(
                TaskLlm.ESTRUTURACAO_AULA,
                new TaskInput.LessonStructuring("Hoje trabalhamos a escala de Dó maior."),
                AiLessonReportOutput.class);
    }

    @Test
    void saidaValidaEmModoJsonDescartaCamposDesconhecidosECalculaCusto() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO)).willReturn(aResponse().withBody(resposta(VALIDO))));

        ResponseLlm<AiLessonReportOutput> resposta = estruturar();

        assertThat(resposta.output().conteudosTrabalhados()).containsExactly("Escala de Dó maior");
        assertThat(resposta.versionPrompt()).isEqualTo("estruturacao-aula.v1");
        assertThat(resposta.inputTokens()).isEqualTo(1000);
        assertThat(resposta.outputTokens()).isEqualTo(500);
        // 1000 × 2 + 500 × 8 = 6000 por milhão
        assertThat(resposta.estimatedCost()).isEqualByComparingTo("0.006");
        SERVIDOR.verify(
                postRequestedFor(urlEqualTo(CAMINHO))
                        .withHeader("Authorization", equalTo("Bearer chave-teste"))
                        .withRequestBody(
                                matchingJsonPath("$.response_format.type", equalTo("json_object")))
                        .withRequestBody(matchingJsonPath("$.temperature", equalTo("0.2")))
                        .withRequestBody(matchingJsonPath("$.model", equalTo("modelo-teste"))));
    }

    @Test
    void saidaInvalidaPedeUmaCorrecao() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .inScenario("correcao")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withBody(resposta("{\"evolucao\": 3}")))
                        .willSetStateTo("corrigir"));
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .inScenario("correcao")
                        .whenScenarioStateIs("corrigir")
                        .willReturn(
                                aResponse().withBody(resposta("```json\n" + VALIDO + "\n```"))));

        ResponseLlm<AiLessonReportOutput> resposta = estruturar();

        assertThat(resposta.output().evolucao()).isEqualTo("Boa evolução.");
        assertThat(resposta.inputTokens()).isEqualTo(2000);
        SERVIDOR.verify(2, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    void saidaInvalidaDuasVezesFalha() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .willReturn(aResponse().withBody(resposta("isto não é JSON"))));

        assertThatThrownBy(this::estruturar).isInstanceOf(InvalidAiOutputException.class);
        SERVIDOR.verify(2, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    void saidaInvalidaDuasVezesLevaOConsumoDasDuasChamadas() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .willReturn(aResponse().withBody(resposta("isto não é JSON"))));

        assertThatThrownBy(this::estruturar)
                .isInstanceOfSatisfying(
                        InvalidAiOutputException.class,
                        falha -> {
                            assertThat(falha.inputTokens()).isEqualTo(2000);
                            assertThat(falha.outputTokens()).isEqualTo(1000);
                            // 2000 × 2 + 1000 × 8 = 12000 por milhão
                            assertThat(falha.estimatedCost()).isEqualByComparingTo("0.012");
                        });
    }

    @Test
    void provedorForaDoArNaChamadaDeCorrecaoLevaOConsumoDaPrimeiraChamada() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .inScenario("correcao-fora-do-ar")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withBody(resposta("{\"evolucao\": 3}")))
                        .willSetStateTo("fora"));
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .inScenario("correcao-fora-do-ar")
                        .whenScenarioStateIs("fora")
                        .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(this::estruturar)
                .isInstanceOfSatisfying(
                        ProviderUnavailableException.class,
                        falha -> {
                            assertThat(falha.inputTokens()).isEqualTo(1000);
                            assertThat(falha.outputTokens()).isEqualTo(500);
                            // 1000 × 2 + 500 × 8 = 6000 por milhão
                            assertThat(falha.estimatedCost()).isEqualByComparingTo("0.006");
                        });
    }

    @Test
    void provedorForaDoArNaPrimeiraChamadaNaoTemConsumo() {
        SERVIDOR.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(this::estruturar)
                .isInstanceOfSatisfying(
                        ProviderUnavailableException.class,
                        falha -> {
                            assertThat(falha.inputTokens()).isNull();
                            assertThat(falha.estimatedCost()).isNull();
                        });
    }

    @Test
    void erro429TemUmaNovaTentativa() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .inScenario("limite")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withStatus(429))
                        .willSetStateTo("liberado"));
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .inScenario("limite")
                        .whenScenarioStateIs("liberado")
                        .willReturn(aResponse().withBody(resposta(VALIDO))));

        assertThat(estruturar().output().atividadesPropostas()).containsExactly("Estudar a escala");
        SERVIDOR.verify(2, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    void falhaDeRedePersistenteDeixaOProvedorIndisponivel() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(this::estruturar).isInstanceOf(ProviderUnavailableException.class);
        SERVIDOR.verify(2, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    void erro5xxPersistenteDeixaOProvedorIndisponivel() {
        SERVIDOR.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(this::estruturar).isInstanceOf(ProviderUnavailableException.class);
    }
}
