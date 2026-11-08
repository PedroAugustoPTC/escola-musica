package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.usecase.operations.EvaluateOperationalAlertsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agenda a apuração de alertas (FR-055, FR-056) no intervalo de {@code
 * reports.alerts.evaluation-seconds} (padrão 60 s, abaixo dos 5 minutos de SC-006). Só chama o caso
 * de uso; exceção nunca derruba o agendador e vai ao log sem dado pessoal.
 */
@Component
@Profile("!seed")
class OperationalAlertTask {

    private static final Logger LOG = LoggerFactory.getLogger(OperationalAlertTask.class);

    private final EvaluateOperationalAlertsUseCase evaluation;

    OperationalAlertTask(EvaluateOperationalAlertsUseCase evaluation) {
        this.evaluation = evaluation;
    }

    @Scheduled(
            fixedDelayString = "#{@alertThresholds.evaluationInterval().toMillis()}",
            initialDelayString = "#{@alertThresholds.evaluationInterval().toMillis()}")
    void run() {
        try {
            evaluation.run();
        } catch (RuntimeException failure) {
            LOG.error(
                    "Apuração de alertas interrompida por erro inesperado ({}).",
                    failure.getClass().getSimpleName());
        }
    }
}
