package br.com.felipeblaft.reports.outbound.audio;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Defesa em profundidade para FR-018: ao iniciar, apaga sobras do diretório temporário (queda no
 * meio do processamento); periodicamente, apaga arquivos com mais de 30 minutos no volume do
 * servidor local do Telegram e no diretório temporário.
 */
@Component
@Profile("!seed")
public class TemporaryAudioCleanupTask {

    private static final Logger LOG = LoggerFactory.getLogger(TemporaryAudioCleanupTask.class);
    static final Duration MAX_AGE = Duration.ofMinutes(30);

    private final Path temporaries;
    private final Path filesTelegram;

    public TemporaryAudioCleanupTask(ApplicationProperties properties) {
        this.temporaries = properties.audio().tempDir();
        this.filesTelegram = properties.telegram().filesDir();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        int deleted = delete(temporaries, Instant.MAX);
        if (deleted > 0) {
            LOG.info("audios-temporarios-apagados-ao-iniciar quantidade={}", deleted);
        }
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void scan() {
        Instant limit = Instant.now().minus(MAX_AGE);
        int deleted = delete(filesTelegram, limit) + delete(temporaries, limit);
        if (deleted > 0) {
            LOG.info("audios-antigos-apagados quantidade={}", deleted);
        }
    }

    /** Apaga os arquivos modificados antes do limite; devolve quantos apagou. */
    static int delete(Path directory, Instant modifiedBefore) {
        if (directory == null || !Files.isDirectory(directory)) {
            return 0;
        }
        int[] deleted = {0};
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> modifiedBefore(file, modifiedBefore))
                    .forEach(
                            file -> {
                                try {
                                    Files.deleteIfExists(file);
                                    deleted[0]++;
                                } catch (IOException error) {
                                    LOG.warn(
                                            "audio-nao-apagado tipo={}",
                                            error.getClass().getSimpleName());
                                }
                            });
        } catch (IOException error) {
            LOG.warn("varredura-audios-falhou tipo={}", error.getClass().getSimpleName());
        }
        return deleted[0];
    }

    private static boolean modifiedBefore(Path file, Instant limit) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isBefore(limit);
        } catch (IOException error) {
            return false;
        }
    }
}
