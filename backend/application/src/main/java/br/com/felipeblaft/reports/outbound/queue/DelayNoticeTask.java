package br.com.felipeblaft.reports.outbound.queue;

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
@Profile("!seed")
public class DelayNoticeTask {

    private final JobQueuePort queue;
    private final TeacherNotificationPort notification;
    private final ClockPort clock;

    public DelayNoticeTask(
            JobQueuePort queue, TeacherNotificationPort notification, ClockPort clock) {
        this.queue = queue;
        this.notification = notification;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void verify() {
        for (ProcessingJob job :
                queue.listDelayedWithoutNotice(clock.now().minus(ProcessingJob.DELAY_LIMIT))) {
            if (queue.markDelayNotice(job.id())) {
                job.markDelayNotice();
                notification.notifyDelay(job);
            }
        }
    }
}
