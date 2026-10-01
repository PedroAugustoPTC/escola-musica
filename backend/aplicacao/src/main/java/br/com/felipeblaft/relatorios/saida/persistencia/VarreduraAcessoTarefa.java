package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.entrada.telegram.RegistroUpdatesTelegram;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.SessaoWebRepositorio;
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

    private final SessaoWebRepositorio sessoes;
    private final RegistroUpdatesTelegram updates;
    private final RelogioPorta relogio;

    VarreduraAcessoTarefa(
            SessaoWebRepositorio sessoes, RegistroUpdatesTelegram updates, RelogioPorta relogio) {
        this.sessoes = sessoes;
        this.updates = updates;
        this.relogio = relogio;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "America/Sao_Paulo")
    void varrer() {
        Instant agora = relogio.agora();
        int sessoesRemovidas = sessoes.removerVencidas(agora);
        int updatesRemovidos = updates.removerAntigos(agora.minus(RETENCAO_UPDATES));
        LOG.info("varredura-acesso sessoes={} updates={}", sessoesRemovidas, updatesRemovidos);
    }
}
