package br.com.felipeblaft.relatorios.saida.ia;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.porta.AudioNaoProcessavelExcecao;
import br.com.felipeblaft.relatorios.porta.AudioNormalizado;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.ResultadoTranscricao;
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
class TranscricaoOpenAiCompativelAdaptadorIT {

    private static final WireMockServer SERVIDOR =
            new WireMockServer(wireMockConfig().dynamicPort());
    private static final String CAMINHO = "/v1/audio/transcriptions";

    @TempDir Path temporario;

    private TranscricaoOpenAiCompativelAdaptador adaptador;
    private AudioNormalizado audio;

    @BeforeAll
    static void iniciar() {
        SERVIDOR.start();
    }

    @AfterAll
    static void parar() {
        SERVIDOR.stop();
    }

    @BeforeEach
    void preparar() throws IOException {
        SERVIDOR.resetAll();
        adaptador =
                new TranscricaoOpenAiCompativelAdaptador(
                        new PropriedadesAplicacao.Transcricao(
                                SERVIDOR.baseUrl() + "/v1",
                                "transcritor-teste",
                                "chave",
                                new BigDecimal("0.36")),
                        new ClienteHttpIa(
                                HttpClient.newHttpClient(),
                                Duration.ofMillis(10),
                                Clock.systemUTC()));
        Path arquivo = temporario.resolve("normalizado.ogg");
        Files.write(arquivo, new byte[] {1, 2, 3, 4});
        audio = new AudioNormalizado(arquivo, 100);
    }

    @Test
    void enviaAudioOggEmPortuguesECalculaOCustoPorSegundo() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO))
                        .willReturn(aResponse().withBody("{\"text\":\" Aula de piano. \"}")));

        ResultadoTranscricao resultado = adaptador.transcrever(audio);

        assertThat(resultado.texto()).isEqualTo("Aula de piano.");
        // 100 s × 0,36 / 3600
        assertThat(resultado.custoEstimado()).isEqualByComparingTo("0.01");
        SERVIDOR.verify(
                postRequestedFor(urlEqualTo(CAMINHO))
                        .withRequestBody(containing("filename=\"audio.ogg\""))
                        .withRequestBody(containing("name=\"language\"\r\n\r\npt"))
                        .withRequestBody(containing("transcritor-teste")));
    }

    @Test
    void textoVazioESemFala() {
        SERVIDOR.stubFor(
                post(urlEqualTo(CAMINHO)).willReturn(aResponse().withBody("{\"text\":\"  \"}")));

        assertThatThrownBy(() -> adaptador.transcrever(audio))
                .isInstanceOfSatisfying(
                        AudioNaoProcessavelExcecao.class,
                        e ->
                                assertThat(e.motivo())
                                        .isEqualTo(AudioNaoProcessavelExcecao.Motivo.SEM_FALA));
    }

    @Test
    void erro4xxEFormatoInvalido() {
        SERVIDOR.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> adaptador.transcrever(audio))
                .isInstanceOfSatisfying(
                        AudioNaoProcessavelExcecao.class,
                        e ->
                                assertThat(e.motivo())
                                        .isEqualTo(AudioNaoProcessavelExcecao.Motivo.FORMATO));
    }

    @Test
    void erro5xxDeixaOProvedorIndisponivelDepoisDeUmaNovaTentativa() {
        SERVIDOR.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(502)));

        assertThatThrownBy(() -> adaptador.transcrever(audio))
                .isInstanceOf(ProvedorIndisponivelExcecao.class);
        SERVIDOR.verify(2, postRequestedFor(urlEqualTo(CAMINHO)));
    }

    @Test
    void credencialRecusadaEIndisponibilidadeENaoFormato() {
        SERVIDOR.stubFor(post(urlEqualTo(CAMINHO)).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> adaptador.transcrever(audio))
                .isInstanceOf(ProvedorIndisponivelExcecao.class);
    }
}
