package br.com.felipeblaft.relatorios.saida.ia;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.porta.EntradaTarefa;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.RespostaLlm;
import br.com.felipeblaft.relatorios.porta.SaidaIaInvalidaExcecao;
import br.com.felipeblaft.relatorios.porta.SaidaRelatorioAulaIa;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
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

    private RespostaLlm<SaidaRelatorioAulaIa> estruturar() {
        return adaptador.gerarJson(
                TarefaLlm.ESTRUTURACAO_AULA,
                new EntradaTarefa.EstruturacaoAula("Hoje trabalhamos a escala de Dó maior."),
                SaidaRelatorioAulaIa.class);
    }

    @Test
    void saidaValidaEmModoJsonDescartaCamposDesconhecidosECalculaCusto() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO)).willReturn(aResponse().withBody(resposta(VALIDO))));

        RespostaLlm<SaidaRelatorioAulaIa> resposta = estruturar();

        assertThat(resposta.saida().conteudosTrabalhados()).containsExactly("Escala de Dó maior");
        assertThat(resposta.versaoPrompt()).isEqualTo("estruturacao-aula.v1");
        assertThat(resposta.tokensEntrada()).isEqualTo(1000);
        assertThat(resposta.tokensSaida()).isEqualTo(500);
        // 1000 × 2 + 500 × 8 = 6000 por milhão
        assertThat(resposta.custoEstimado()).isEqualByComparingTo("0.006");
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

        RespostaLlm<SaidaRelatorioAulaIa> resposta = estruturar();

        assertThat(resposta.saida().evolucao()).isEqualTo("Boa evolução.");
        assertThat(resposta.tokensEntrada()).isEqualTo(2000);
        SERVIDOR.verify(2, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    void saidaInvalidaDuasVezesFalha() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .willReturn(aResponse().withBody(resposta("isto não é JSON"))));

        assertThatThrownBy(this::estruturar).isInstanceOf(SaidaIaInvalidaExcecao.class);
        SERVIDOR.verify(2, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    void saidaInvalidaDuasVezesLevaOConsumoDasDuasChamadas() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .willReturn(aResponse().withBody(resposta("isto não é JSON"))));

        assertThatThrownBy(this::estruturar)
                .isInstanceOfSatisfying(
                        SaidaIaInvalidaExcecao.class,
                        falha -> {
                            assertThat(falha.tokensEntrada()).isEqualTo(2000);
                            assertThat(falha.tokensSaida()).isEqualTo(1000);
                            // 2000 × 2 + 1000 × 8 = 12000 por milhão
                            assertThat(falha.custoEstimado()).isEqualByComparingTo("0.012");
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

        assertThat(estruturar().saida().atividadesPropostas()).containsExactly("Estudar a escala");
        SERVIDOR.verify(2, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    void falhaDeRedePersistenteDeixaOProvedorIndisponivel() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(this::estruturar).isInstanceOf(ProvedorIndisponivelExcecao.class);
        SERVIDOR.verify(2, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    void erro5xxPersistenteDeixaOProvedorIndisponivel() {
        SERVIDOR.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(this::estruturar).isInstanceOf(ProvedorIndisponivelExcecao.class);
    }
}
