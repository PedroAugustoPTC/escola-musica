package br.com.felipeblaft.reports.saida.fila;

import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.TeacherNotificationPort;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aos 3 minutos sem conclusão, avisa uma vez que está demorando (caso de borda "processamento que
 * excede o tempo esperado"). A Web vê {@code demorando} na consulta do trabalho.
 */
@Component
@Profile("!carga")
public class AvisoDemoraTarefa {

    private final JobQueuePort fila;
    private final TeacherNotificationPort notificacao;
    private final ClockPort relogio;

    public AvisoDemoraTarefa(
            JobQueuePort fila, TeacherNotificationPort notificacao, ClockPort relogio) {
        this.fila = fila;
        this.notificacao = notificacao;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void verificar() {
        for (ProcessingJob trabalho :
                fila.listDelayedWithoutNotice(relogio.now().minus(ProcessingJob.DELAY_LIMIT))) {
            if (fila.markDelayNotice(trabalho.id())) {
                trabalho.markDelayNotice();
                notificacao.notifyDelay(trabalho);
            }
        }
    }
}
