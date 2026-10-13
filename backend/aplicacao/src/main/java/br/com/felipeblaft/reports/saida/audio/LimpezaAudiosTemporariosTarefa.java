package br.com.felipeblaft.reports.saida.audio;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
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
@Profile("!carga")
public class LimpezaAudiosTemporariosTarefa {

    private static final Logger LOG = LoggerFactory.getLogger(LimpezaAudiosTemporariosTarefa.class);
    static final Duration IDADE_MAXIMA = Duration.ofMinutes(30);

    private final Path temporarios;
    private final Path arquivosTelegram;

    public LimpezaAudiosTemporariosTarefa(PropriedadesAplicacao propriedades) {
        this.temporarios = propriedades.audio().tempDir();
        this.arquivosTelegram = propriedades.telegram().arquivosDir();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        int apagados = apagar(temporarios, Instant.MAX);
        if (apagados > 0) {
            LOG.info("audios-temporarios-apagados-ao-iniciar quantidade={}", apagados);
        }
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void varrer() {
        Instant limite = Instant.now().minus(IDADE_MAXIMA);
        int apagados = apagar(arquivosTelegram, limite) + apagar(temporarios, limite);
        if (apagados > 0) {
            LOG.info("audios-antigos-apagados quantidade={}", apagados);
        }
    }

    /** Apaga os arquivos modificados antes do limite; devolve quantos apagou. */
    static int apagar(Path diretorio, Instant modificadosAntesDe) {
        if (diretorio == null || !Files.isDirectory(diretorio)) {
            return 0;
        }
        int[] apagados = {0};
        try (Stream<Path> arquivos = Files.walk(diretorio)) {
            arquivos.filter(Files::isRegularFile)
                    .filter(arquivo -> modificadoAntes(arquivo, modificadosAntesDe))
                    .forEach(
                            arquivo -> {
                                try {
                                    Files.deleteIfExists(arquivo);
                                    apagados[0]++;
                                } catch (IOException erro) {
                                    LOG.warn(
                                            "audio-nao-apagado tipo={}",
                                            erro.getClass().getSimpleName());
                                }
                            });
        } catch (IOException erro) {
            LOG.warn("varredura-audios-falhou tipo={}", erro.getClass().getSimpleName());
        }
        return apagados[0];
    }

    private static boolean modificadoAntes(Path arquivo, Instant limite) {
        try {
            return Files.getLastModifiedTime(arquivo).toInstant().isBefore(limite);
        } catch (IOException erro) {
            return false;
        }
    }
}
