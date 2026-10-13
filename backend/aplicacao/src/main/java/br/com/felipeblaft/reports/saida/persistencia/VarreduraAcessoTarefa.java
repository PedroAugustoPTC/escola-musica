package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.entrada.telegram.RegistroUpdatesTelegram;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Varredura diária: sessões vencidas e updates do Telegram com mais de 7 dias. */
@Component
@Profile("!carga")
class VarreduraAcessoTarefa {

    private static final Logger LOG = LoggerFactory.getLogger(VarreduraAcessoTarefa.class);
    static final Duration RETENCAO_UPDATES = Duration.ofDays(7);

    private final WebSessionRepository sessoes;
    private final RegistroUpdatesTelegram updates;
    private final ClockPort relogio;

    VarreduraAcessoTarefa(
            WebSessionRepository sessoes, RegistroUpdatesTelegram updates, ClockPort relogio) {
        this.sessoes = sessoes;
        this.updates = updates;
        this.relogio = relogio;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "America/Sao_Paulo")
    void varrer() {
        Instant agora = relogio.now();
        int sessoesRemovidas = sessoes.removeExpired(agora);
        int updatesRemovidos = updates.removerAntigos(agora.minus(RETENCAO_UPDATES));
        LOG.info("varredura-acesso sessoes={} updates={}", sessoesRemovidas, updatesRemovidos);
    }
}
