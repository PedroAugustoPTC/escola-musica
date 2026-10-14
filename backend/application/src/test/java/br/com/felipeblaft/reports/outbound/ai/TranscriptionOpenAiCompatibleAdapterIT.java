package br.com.felipeblaft.reports.outbound.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.NormalizedAudio;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.TranscriptionResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adaptador de transcrição contra um servidor compatível com OpenAI simulado no WireMock (R-08).
 */
class TranscriptionOpenAiCompatibleAdapterIT {

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());
    private static final String PATH = "/v1/audio/transcriptions";

    @TempDir Path temporary;

    private TranscriptionOpenAiCompatibleAdapter adapter;
    private NormalizedAudio audio;

    @BeforeAll
    static void start() {
        SERVER.start();
    }

    @AfterAll
    static void stop() {
        SERVER.stop();
    }

    @BeforeEach
    void prepare() throws IOException {
        SERVER.resetAll();
        adapter =
                new TranscriptionOpenAiCompatibleAdapter(
                        new ApplicationProperties.Transcription(
                                SERVER.baseUrl() + "/v1",
                                "transcritor-teste",
                                "chave",
                                new BigDecimal("0.36")),
                        new AiHttpClient(
                                HttpClient.newHttpClient(),
                                Duration.ofMillis(10),
                                Clock.systemUTC()));
        Path file = temporary.resolve("normalizado.ogg");
        Files.write(file, new byte[] {1, 2, 3, 4});
        audio = new NormalizedAudio(file, 100);
    }

    @Test
    void sendsOggAudioInPortugueseAndCalculatesCostPerSecond() {
        SERVER.stubFor(
                post(urlEqualTo(PATH))
                        .willReturn(aResponse().withBody("{\"text\":\" Aula de piano. \"}")));

        TranscriptionResult result = adapter.transcribe(audio);

        assertThat(result.text()).isEqualTo("Aula de piano.");
        // 100 s × 0,36 / 3600
        assertThat(result.estimatedCost()).isEqualByComparingTo("0.01");
        SERVER.verify(
                postRequestedFor(urlEqualTo(PATH))
                        .withRequestBody(containing("filename=\"audio.ogg\""))
                        .withRequestBody(containing("name=\"language\"\r\n\r\npt"))
                        .withRequestBody(containing("transcritor-teste")));
    }

    @Test
    void emptyTextAndNoSpeech() {
        SERVER.stubFor(
                post(urlEqualTo(PATH)).willReturn(aResponse().withBody("{\"text\":\"  \"}")));

        assertThatThrownBy(() -> adapter.transcribe(audio))
                .isInstanceOfSatisfying(
                        AudioNotProcessableException.class,
                        e ->
                                assertThat(e.reason())
                                        .isEqualTo(AudioNotProcessableException.Reason.SEM_FALA));
    }

    @Test
    void error4xxAndInvalidFormat() {
        SERVER.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> adapter.transcribe(audio))
                .isInstanceOfSatisfying(
                        AudioNotProcessableException.class,
                        e ->
                                assertThat(e.reason())
                                        .isEqualTo(AudioNotProcessableException.Reason.FORMATO));
    }

    @Test
    void error5xxLeavesProviderUnavailableAfterOneNewAttempt() {
        SERVER.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(502)));

        assertThatThrownBy(() -> adapter.transcribe(audio))
                .isInstanceOf(ProviderUnavailableException.class);
        SERVER.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void refusedCredentialAndUnavailabilityAndNotFormat() {
        SERVER.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> adapter.transcribe(audio))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}
