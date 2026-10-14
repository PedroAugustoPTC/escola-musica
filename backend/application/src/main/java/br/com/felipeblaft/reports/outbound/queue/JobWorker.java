package br.com.felipeblaft.reports.outbound.queue;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.outbound.persistence.JobQueuePostgresAdapter;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.service.JobProcessorService;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consome a fila de trabalhos em threads virtuais (R-06). O número de trabalhos simultâneos é
 * configurável ({@code TRABALHOS_WORKERS}); com zero, a fila não é consumida (testes).
 */
@Component
@Profile("!seed")
public class JobWorker {

    private static final Logger LOG = LoggerFactory.getLogger(JobWorker.class);

    private final JobQueuePort queue;
    private final JobQueuePostgresAdapter queuePostgres;
    private final JobProcessorService processor;
    private final ClockPort clock;
    private final Semaphore slots;
    private final int workers;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public JobWorker(
            JobQueuePort queue,
            JobQueuePostgresAdapter queuePostgres,
            JobProcessorService processor,
            ClockPort clock,
            ApplicationProperties properties) {
        this.queue = queue;
        this.queuePostgres = queuePostgres;
        this.processor = processor;
        this.clock = clock;
        this.workers = properties.jobs().workers();
        this.slots = new Semaphore(Math.max(0, workers));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        int recovered = queuePostgres.recoverStuck(clock.now());
        if (recovered > 0) {
            LOG.info("jobs-recovered quantity={}", recovered);
        }
    }

    @Scheduled(fixedDelayString = "${reports.jobs.interval-ms:500}")
    public void consume() {
        if (workers <= 0) {
            return;
        }
        while (slots.tryAcquire()) {
            Optional<ProcessingJob> next;
            try {
                next = queue.reserveNext(clock.now());
            } catch (RuntimeException error) {
                slots.release();
                LOG.warn("queue-unavailable type={}", error.getClass().getSimpleName());
                return;
            }
            if (next.isEmpty()) {
                slots.release();
                return;
            }
            ProcessingJob job = next.get();
            executor.submit(() -> run(job));
        }
    }

    /**
     * Primeiro ponto do código da aplicação na pilha, para depurar sem expor o conteúdo do erro.
     */
    private static String localOf(Throwable error) {
        for (StackTraceElement point : error.getStackTrace()) {
            if (point.getClassName().startsWith("br.com.felipeblaft")) {
                return point.getClassName()
                        + "."
                        + point.getMethodName()
                        + ":"
                        + point.getLineNumber();
            }
        }
        return "unknown";
    }

    void run(ProcessingJob job) {
        MDC.put("jobId", job.id().toString());
        MDC.put("channel", job.requestChannel().name());
        long start = System.currentTimeMillis();
        try {
            processor.process(job);
            LOG.info(
                    "job-completed type={} result={} durationMs={}",
                    job.type(),
                    job.result(),
                    System.currentTimeMillis() - start);
        } catch (RuntimeException | Error error) {
            // o executor descartaria o erro em silêncio: registra sempre, mas só o tipo do
            // trabalho,
            // o da exceção e o ponto do código; a mensagem e a causa podem trazer SQL, texto de
            // relatório ou dado de aluno (FR-055)
            LOG.error(
                    "job-failed type={} error={} local={}",
                    job.type(),
                    error.getClass().getName(),
                    localOf(error));
        } finally {
            MDC.clear();
            slots.release();
        }
    }
}
