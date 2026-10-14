package br.com.felipeblaft.reports.outbound.ai;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.NormalizedAudio;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.TranscriptionPort;
import br.com.felipeblaft.reports.port.TranscriptionResult;
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
public class TranscriptionOpenAiCompatibleAdapter implements TranscriptionPort {

    static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ApplicationProperties.Transcription config;
    private final AiHttpClient http;

    public TranscriptionOpenAiCompatibleAdapter(ApplicationProperties.Transcription config) {
        this(config, new AiHttpClient(Duration.ofSeconds(1)));
    }

    TranscriptionOpenAiCompatibleAdapter(
            ApplicationProperties.Transcription config, AiHttpClient http) {
        this.config = Objects.requireNonNull(config);
        this.http = http;
    }

    @Override
    public TranscriptionResult transcribe(NormalizedAudio audio) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(audio.path());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        String boundary = "----transcription" + UUID.randomUUID();
        byte[] body = multipart(boundary, bytes);
        AiHttpClient.Response response =
                http.run(
                        () ->
                                AiHttpClient.base(
                                                config.url() + "/audio/transcriptions",
                                                config.key(),
                                                TIMEOUT)
                                        .header(
                                                "Content-Type",
                                                "multipart/form-data; boundary=" + boundary)
                                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                                        .build());
        if (!response.success()) {
            if (response.status() == 401 || response.status() == 403) {
                throw new ProviderUnavailableException("status-" + response.status());
            }
            throw new AudioNotProcessableException(AudioNotProcessableException.Reason.FORMAT);
        }
        String text;
        try {
            text = JSON.readTree(response.body()).path("text").asString("");
        } catch (JacksonException error) {
            throw new ProviderUnavailableException("invalid-answer");
        }
        if (text.isBlank()) {
            throw new AudioNotProcessableException(AudioNotProcessableException.Reason.NO_SPEECH);
        }
        BigDecimal price = Objects.requireNonNullElse(config.priceUsdPerHour(), BigDecimal.ZERO);
        BigDecimal cost =
                BigDecimal.valueOf(audio.durationSeconds())
                        .multiply(price)
                        .divide(SECONDS_PER_HOUR, MathContext.DECIMAL64);
        return new TranscriptionResult(
                text.strip(),
                audio.durationSeconds(),
                cost,
                AiHttpClient.modelProvider(config.url(), config.model()));
    }

    private byte[] multipart(String boundary, byte[] audio) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(audio.length + 1024);
        field(output, boundary, "model", config.model());
        field(output, boundary, "language", NormalizedAudio.LANGUAGE);
        field(output, boundary, "response_format", "json");
        field(output, boundary, "temperature", "0");
        write(
                output,
                "--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.ogg\""
                        + "\r\nContent-Type: audio/ogg\r\n\r\n");
        output.writeBytes(audio);
        write(output, "\r\n--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private static void field(
            ByteArrayOutputStream output, String boundary, String name, String value) {
        write(
                output,
                "--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\""
                        + name
                        + "\"\r\n\r\n"
                        + value
                        + "\r\n");
    }

    private static void write(ByteArrayOutputStream output, String text) {
        output.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
