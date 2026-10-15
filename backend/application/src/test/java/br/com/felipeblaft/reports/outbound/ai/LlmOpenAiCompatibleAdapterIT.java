package br.com.felipeblaft.reports.outbound.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.config.ApplicationProperties;
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
class LlmOpenAiCompatibleAdapterIT {

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PATH = "/v1/chat/completions";

    private static final String VALID =
            """
            {"workedTopics":["Escala de Dó maior"],"progress":"Boa evolução.",
             "difficulties":[],"proposedActivities":["Estudar a escala"],"observations":"",
             "pendingQuestions":[],"inventedField":"descartar"}
            """;

    private LlmOpenAiCompatibleAdapter adapter;

    @BeforeAll
    static void start() {
        SERVER.start();
    }

    @AfterAll
    static void stop() {
        SERVER.stop();
    }

    @BeforeEach
    void prepare() {
        SERVER.resetAll();
        ApplicationProperties.Llm config =
                new ApplicationProperties.Llm(
                        SERVER.baseUrl() + "/v1",
                        "test-model",
                        "test-key",
                        new BigDecimal("2"),
                        new BigDecimal("8"));
        adapter =
                new LlmOpenAiCompatibleAdapter(
                        config,
                        new AiHttpClient(
                                HttpClient.newHttpClient(),
                                Duration.ofMillis(10),
                                Clock.systemUTC()));
    }

    private static String response(String content) {
        return JSON.writeValueAsString(
                java.util.Map.of(
                        "choices",
                                java.util.List.of(
                                        java.util.Map.of(
                                                "message", java.util.Map.of("content", content))),
                        "usage",
                                java.util.Map.of("prompt_tokens", 1000, "completion_tokens", 500)));
    }

    private ResponseLlm<AiLessonReportOutput> structure() {
        return adapter.generateJson(
                TaskLlm.LESSON_STRUCTURING,
                new TaskInput.LessonStructuring("Hoje trabalhamos a escala de Dó maior."),
                AiLessonReportOutput.class);
    }

    @Test
    void validOutputInJsonModeDiscardsUnknownFieldsAndCalculatesCost() {
        SERVER.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withBody(response(VALID))));

        ResponseLlm<AiLessonReportOutput> response = structure();

        assertThat(response.output().workedTopics()).containsExactly("Escala de Dó maior");
        assertThat(response.versionPrompt()).isEqualTo("lesson-structuring.v1");
        assertThat(response.inputTokens()).isEqualTo(1000);
        assertThat(response.outputTokens()).isEqualTo(500);
        // 1000 × 2 + 500 × 8 = 6000 por milhão
        assertThat(response.estimatedCost()).isEqualByComparingTo("0.006");
        SERVER.verify(
                postRequestedFor(urlEqualTo(PATH))
                        .withHeader("Authorization", equalTo("Bearer test-key"))
                        .withRequestBody(
                                matchingJsonPath("$.response_format.type", equalTo("json_object")))
                        .withRequestBody(matchingJsonPath("$.temperature", equalTo("0.2")))
                        .withRequestBody(matchingJsonPath("$.model", equalTo("test-model"))));
    }

    @Test
    void invalidOutputAsksForOneCorrection() {
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .inScenario("correction")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withBody(response("{\"progress\": 3}")))
                        .willSetStateTo("fix"));
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .inScenario("correction")
                        .whenScenarioStateIs("fix")
                        .willReturn(aResponse().withBody(response("```json\n" + VALID + "\n```"))));

        ResponseLlm<AiLessonReportOutput> response = structure();

        assertThat(response.output().progress()).isEqualTo("Boa evolução.");
        assertThat(response.inputTokens()).isEqualTo(2000);
        SERVER.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void invalidOutputTwiceFails() {
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(aResponse().withBody(response("isto não é JSON"))));

        assertThatThrownBy(this::structure).isInstanceOf(InvalidAiOutputException.class);
        SERVER.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void invalidOutputTwiceCarriesConsumptionOfBothCalls() {
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(aResponse().withBody(response("isto não é JSON"))));

        assertThatThrownBy(this::structure)
                .isInstanceOfSatisfying(
                        InvalidAiOutputException.class,
                        failure -> {
                            assertThat(failure.inputTokens()).isEqualTo(2000);
                            assertThat(failure.outputTokens()).isEqualTo(1000);
                            // 2000 × 2 + 1000 × 8 = 12000 por milhão
                            assertThat(failure.estimatedCost()).isEqualByComparingTo("0.012");
                        });
    }

    @Test
    void providerDownOnCorrectionCallCarriesConsumptionOfFirstCall() {
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .inScenario("correction-offline")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withBody(response("{\"progress\": 3}")))
                        .willSetStateTo("outside"));
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .inScenario("correction-offline")
                        .whenScenarioStateIs("outside")
                        .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(this::structure)
                .isInstanceOfSatisfying(
                        ProviderUnavailableException.class,
                        failure -> {
                            assertThat(failure.inputTokens()).isEqualTo(1000);
                            assertThat(failure.outputTokens()).isEqualTo(500);
                            // 1000 × 2 + 500 × 8 = 6000 por milhão
                            assertThat(failure.estimatedCost()).isEqualByComparingTo("0.006");
                        });
    }

    @Test
    void providerDownOnFirstCallHasNoConsumption() {
        SERVER.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(this::structure)
                .isInstanceOfSatisfying(
                        ProviderUnavailableException.class,
                        failure -> {
                            assertThat(failure.inputTokens()).isNull();
                            assertThat(failure.estimatedCost()).isNull();
                        });
    }

    @Test
    void error429HasOneNewAttempt() {
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .inScenario("limit")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withStatus(429))
                        .willSetStateTo("released"));
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .inScenario("limit")
                        .whenScenarioStateIs("released")
                        .willReturn(aResponse().withBody(response(VALID))));

        assertThat(structure().output().proposedActivities()).containsExactly("Estudar a escala");
        SERVER.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void persistentNetworkFailureLeavesProviderUnavailable() {
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(this::structure).isInstanceOf(ProviderUnavailableException.class);
        SERVER.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void persistentError5xxLeavesProviderUnavailable() {
        SERVER.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(this::structure).isInstanceOf(ProviderUnavailableException.class);
    }
}
