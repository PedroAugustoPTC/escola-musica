package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.privacy.RunRetentionUseCase;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agenda a retenção diária (FR-015). Só chama o caso de uso com o ator da retenção; nenhuma regra
 * aqui. Exceção nunca derruba o agendador: vai ao log, sem dado pessoal, e a execução seguinte
 * retoma de onde parou.
 */
@Component
@Profile("!seed")
class RetentionTask {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionTask.class);

    private final RunRetentionUseCase retention;
    private final ClockPort clock;

    RetentionTask(RunRetentionUseCase retention, ClockPort clock) {
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "${reports.retention.cron}", zone = "${reports.school.timezone}")
    void run() {
        try {
            RunRetentionUseCase.Result result =
                    retention.run(Actor.retention(), UUID.randomUUID().toString());
            if (result.alreadyRunning()) {
                LOG.warn("Retenção não iniciada: outra execução está em curso.");
                return;
            }
            LOG.info(
                    "Retenção concluída em {}: situação {}, itens {}, falhas {}.",
                    clock.now(),
                    result.status(),
                    result.counts(),
                    result.failures());
        } catch (RuntimeException failure) {
            LOG.error("Retenção interrompida por erro inesperado ({}).", failure.getClass().getSimpleName());
        }
    }
}
