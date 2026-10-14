package br.com.felipeblaft.reports.outbound.audio;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.port.AudioNormalizationPort;
import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.NormalizedAudio;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.port.UnsupportedFormatException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Normalização com FFmpeg (R-07): extrai só a trilha de áudio (inclusive de vídeo MP4), converte
 * para Ogg/Opus mono 16 kHz 24 kbit/s no diretório temporário ({@code tmpfs}) e mede a duração com
 * ffprobe. Sem trilha de áudio ou contêiner desconhecido → formato não suportado; menos de 1 s →
 * áudio vazio.
 */
@Component
public class FfmpegAudioNormalizationAdapter implements AudioNormalizationPort {

    private static final Logger LOG =
            LoggerFactory.getLogger(FfmpegAudioNormalizationAdapter.class);
    static final double MINIMUM_DURATION = 1.0;
    static final long TIMEOUT_SECONDS = 300;

    private final Path directory;

    @Autowired
    public FfmpegAudioNormalizationAdapter(ApplicationProperties properties) {
        this(properties.audio().tempDir());
    }

    public FfmpegAudioNormalizationAdapter(Path directory) {
        this.directory = directory;
    }

    @Override
    public NormalizedAudio normalize(TemporaryFile original) {
        Path output = directory.resolve("normalizado-" + UUID.randomUUID() + ".ogg");
        try {
            Files.createDirectories(directory);
            int code =
                    run(
                            List.of(
                                    "ffmpeg",
                                    "-nostdin",
                                    "-y",
                                    "-hide_banner",
                                    "-loglevel",
                                    "error",
                                    "-i",
                                    original.path().toString(),
                                    "-vn",
                                    "-ac",
                                    "1",
                                    "-ar",
                                    "16000",
                                    "-c:a",
                                    "libopus",
                                    "-b:a",
                                    "24k",
                                    output.toString()));
            if (code != 0 || !Files.exists(output) || Files.size(output) == 0) {
                delete(output);
                throw new UnsupportedFormatException();
            }
            double duration = duration(output);
            if (duration < MINIMUM_DURATION) {
                delete(output);
                throw new AudioNotProcessableException(AudioNotProcessableException.Reason.VAZIO);
            }
            return new NormalizedAudio(output, duration);
        } catch (IOException error) {
            delete(output);
            throw new UnsupportedFormatException();
        }
    }

    double duration(Path file) throws IOException {
        Process process =
                new ProcessBuilder(
                                "ffprobe",
                                "-v",
                                "error",
                                "-show_entries",
                                "format=duration",
                                "-of",
                                "default=noprint_wrappers=1:nokey=1",
                                file.toString())
                        .redirectErrorStream(true)
                        .start();
        String text =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        await(process);
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static int run(List<String> command) throws IOException {
        Process process =
                new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
        return await(process);
    }

    private static int await(Process process) throws IOException {
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warn("ffmpeg-timeout");
                return -1;
            }
            return process.exitValue();
        } catch (InterruptedException error) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrompido", error);
        }
    }

    @Override
    public void delete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            LOG.warn("audio-nao-apagado tipo={}", error.getClass().getSimpleName());
        }
    }
}
