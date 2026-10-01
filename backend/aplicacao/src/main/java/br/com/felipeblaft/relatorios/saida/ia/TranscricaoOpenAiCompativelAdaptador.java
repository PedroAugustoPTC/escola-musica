package br.com.felipeblaft.relatorios.saida.ia;

import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.porta.AudioNaoProcessavelExcecao;
import br.com.felipeblaft.relatorios.porta.AudioNormalizado;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.ResultadoTranscricao;
import br.com.felipeblaft.relatorios.porta.TranscricaoPorta;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transcrição pela API {@code audio/transcriptions} compatível com a da OpenAI (R-08): envia sempre
 * {@code audio.ogg} (já normalizado, R-07) com {@code language=pt}; timeout de 60 s. Não guarda o
 * áudio: quem apaga o arquivo é o AudioTemporarioServico.
 */
public class TranscricaoOpenAiCompativelAdaptador implements TranscricaoPorta {

    static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final BigDecimal SEGUNDOS_HORA = BigDecimal.valueOf(3600);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PropriedadesAplicacao.Transcricao configuracao;
    private final ClienteHttpIa http;

    public TranscricaoOpenAiCompativelAdaptador(PropriedadesAplicacao.Transcricao configuracao) {
        this(configuracao, new ClienteHttpIa(Duration.ofSeconds(1)));
    }

    TranscricaoOpenAiCompativelAdaptador(
            PropriedadesAplicacao.Transcricao configuracao, ClienteHttpIa http) {
        this.configuracao = Objects.requireNonNull(configuracao);
        this.http = http;
    }

    @Override
    public ResultadoTranscricao transcrever(AudioNormalizado audio) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(audio.caminho());
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
        String fronteira = "----transcricao" + UUID.randomUUID();
        byte[] corpo = multipart(fronteira, bytes);
        ClienteHttpIa.Resposta resposta =
                http.executar(
                        () ->
                                ClienteHttpIa.base(
                                                configuracao.url() + "/audio/transcriptions",
                                                configuracao.chave(),
                                                TIMEOUT)
                                        .header(
                                                "Content-Type",
                                                "multipart/form-data; boundary=" + fronteira)
                                        .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                                        .build());
        if (!resposta.sucesso()) {
            if (resposta.status() == 401 || resposta.status() == 403) {
                throw new ProvedorIndisponivelExcecao("status-" + resposta.status());
            }
            throw new AudioNaoProcessavelExcecao(AudioNaoProcessavelExcecao.Motivo.FORMATO);
        }
        String texto;
        try {
            texto = JSON.readTree(resposta.corpo()).path("text").asString("");
        } catch (JacksonException erro) {
            throw new ProvedorIndisponivelExcecao("resposta-invalida");
        }
        if (texto.isBlank()) {
            throw new AudioNaoProcessavelExcecao(AudioNaoProcessavelExcecao.Motivo.SEM_FALA);
        }
        BigDecimal preco =
                Objects.requireNonNullElse(configuracao.precoUsdPorHora(), BigDecimal.ZERO);
        BigDecimal custo =
                BigDecimal.valueOf(audio.duracaoSegundos())
                        .multiply(preco)
                        .divide(SEGUNDOS_HORA, MathContext.DECIMAL64);
        return new ResultadoTranscricao(
                texto.strip(),
                audio.duracaoSegundos(),
                custo,
                ClienteHttpIa.provedorModelo(configuracao.url(), configuracao.modelo()));
    }

    private byte[] multipart(String fronteira, byte[] audio) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream(audio.length + 1024);
        campo(saida, fronteira, "model", configuracao.modelo());
        campo(saida, fronteira, "language", AudioNormalizado.IDIOMA);
        campo(saida, fronteira, "response_format", "json");
        campo(saida, fronteira, "temperature", "0");
        escrever(
                saida,
                "--"
                        + fronteira
                        + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.ogg\""
                        + "\r\nContent-Type: audio/ogg\r\n\r\n");
        saida.writeBytes(audio);
        escrever(saida, "\r\n--" + fronteira + "--\r\n");
        return saida.toByteArray();
    }

    private static void campo(
            ByteArrayOutputStream saida, String fronteira, String nome, String valor) {
        escrever(
                saida,
                "--"
                        + fronteira
                        + "\r\nContent-Disposition: form-data; name=\""
                        + nome
                        + "\"\r\n\r\n"
                        + valor
                        + "\r\n");
    }

    private static void escrever(ByteArrayOutputStream saida, String texto) {
        saida.writeBytes(texto.getBytes(StandardCharsets.UTF_8));
    }
}
