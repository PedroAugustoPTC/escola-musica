package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.CostRecordPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.OperationalAlertRepository;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.operations.EvaluateOperationalAlertsUseCase;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Alertas contra o Postgres real (SC-006, {@code AdjustableClock}): cada condição simulada aparece
 * como um único alerta ativo em até 5 minutos de relógio e normaliza quando some; o índice único
 * impede dois ativos iguais sob concorrência.
 */
class OperationalAlertIT extends IntegrationBase {

    private static final Duration EVALUATION_STEP = Duration.ofSeconds(60);
    private static final Duration SC_006_LIMIT = Duration.ofMinutes(5);

    @Autowired OperationalAlertService alerts;
    @Autowired OperationalAlertRepository repository;
    @Autowired EvaluateOperationalAlertsUseCase evaluation;
    @Autowired CostRecordPort costs;
    @Autowired JobQueuePort queue;
    @Autowired ChannelActionRepository actions;
    @Autowired ScenarioIT scenario;

    private ProcessingJob job() {
        Teacher clara = scenario.clara();
        Student student = scenario.student(clara, "Matteo Ferreira", "Piano");
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        clara.id(),
                        student.id(),
                        null,
                        null,
                        Channel.WEB,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());
        queue.enqueue(job);
        return job;
    }

    private void aiCall(ProcessingJob job, String provider, boolean success) {
        clock.advance(Duration.ofSeconds(1));
        costs.register(
                AiCall.create(
                        job.id(),
                        Channel.WEB,
                        null,
                        null,
                        AiTask.STRUCTURING,
                        provider,
                        null,
                        null,
                        null,
                        500,
                        BigDecimal.ZERO,
                        success,
                        success ? null : "PROVIDER_UNAVAILABLE",
                        clock.now()));
    }

    /** Avalia a cada passo do agendador e devolve quanto tempo de relógio passou até aparecer. */
    private Duration evaluateUntilActive(AlertType type) {
        Duration elapsed = Duration.ZERO;
        while (elapsed.compareTo(SC_006_LIMIT) <= 0) {
            clock.advance(EVALUATION_STEP);
            elapsed = elapsed.plus(EVALUATION_STEP);
            evaluation.run();
            if (repository.findActive().stream().anyMatch(a -> a.type() == type)) {
                return elapsed;
            }
        }
        return elapsed;
    }

    private List<OperationalAlert> active(AlertType type) {
        return repository.findActive().stream().filter(a -> a.type() == type).toList();
    }

    @Test
    void failingProviderOpensOneAlertAndNormalizesWhenItRecovers() {
        ProcessingJob job = job();
        for (int i = 0; i < 3; i++) {
            aiCall(job, "provider-a/model", false);
        }

        Duration elapsed = evaluateUntilActive(AlertType.AI_PROVIDER_FAILING);

        assertThat(elapsed).isLessThanOrEqualTo(SC_006_LIMIT);
        assertThat(active(AlertType.AI_PROVIDER_FAILING)).hasSize(1);
        assertThat(active(AlertType.AI_PROVIDER_FAILING).getFirst().component())
                .isEqualTo("provider-a-model");

        evaluation.run();
        assertThat(active(AlertType.AI_PROVIDER_FAILING)).hasSize(1);
        assertThat(active(AlertType.AI_PROVIDER_FAILING).getFirst().occurrences()).isGreaterThan(1);

        aiCall(job, "provider-a/model", true);
        for (int i = 0; i < 20; i++) {
            aiCall(job, "provider-a/model", true);
        }
        evaluation.run();
        assertThat(active(AlertType.AI_PROVIDER_FAILING)).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from operational_alert where status ="
                                        + " 'NORMALIZED' and normalized_at is not null",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void oldPendingQueueItemOpensTheAlertAndNormalizesWhenDrained() {
        RetryPolicy policy =
                new RetryPolicy(
                        Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));
        actions.saveBatch(
                List.of(
                        ChannelAction.create(
                                "p:queue:1:0",
                                "batch:queue",
                                ChannelActionOrigin.PROACTIVE,
                                null,
                                7_001L,
                                UUID.randomUUID(),
                                null,
                                ChannelActionType.MESSAGE,
                                "{\"text\":\"queued\"}",
                                null,
                                clock.now(),
                                policy)));

        // a condição passa a valer quando o item passa de 5 minutos de espera
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        Duration elapsed = evaluateUntilActive(AlertType.CHANNEL_QUEUE_BACKLOG);

        assertThat(elapsed).isLessThanOrEqualTo(SC_006_LIMIT);
        assertThat(active(AlertType.CHANNEL_QUEUE_BACKLOG)).hasSize(1);

        jdbc.update("update channel_action set state = 'DISCARDED' where state = 'PENDING'");
        evaluation.run();
        assertThat(active(AlertType.CHANNEL_QUEUE_BACKLOG)).isEmpty();
    }

    @Test
    void storageFailureIsOneAlertThatNormalizesAfterTheQuietPeriod() {
        alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");
        alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "READ_FAILED");

        assertThat(active(AlertType.STORAGE_FAILED)).hasSize(1);
        assertThat(active(AlertType.STORAGE_FAILED).getFirst().occurrences()).isEqualTo(2);

        clock.advance(Duration.ofMinutes(14));
        evaluation.run();
        assertThat(active(AlertType.STORAGE_FAILED)).hasSize(1);

        clock.advance(Duration.ofMinutes(2));
        evaluation.run();
        assertThat(active(AlertType.STORAGE_FAILED)).isEmpty();
    }

    @Test
    void retentionFailureIsOneAlertUntilItIsResolved() {
        alerts.report(AlertType.RETENTION_FAILED, "retention", "RUN_FAILED");
        clock.advance(Duration.ofHours(3));
        evaluation.run();

        assertThat(active(AlertType.RETENTION_FAILED)).hasSize(1);

        alerts.resolve(AlertType.RETENTION_FAILED, "retention");
        assertThat(active(AlertType.RETENTION_FAILED)).isEmpty();
    }

    @Test
    void openingAndNormalizingAreRecordedInTheTrail() {
        alerts.report(AlertType.RETENTION_FAILED, "retention", "RUN_FAILED");
        alerts.resolve(AlertType.RETENTION_FAILED, "retention");

        assertThat(
                        jdbc.queryForList(
                                "select operation from authorship_record where target_type ="
                                        + " 'ALERT' order by sequence",
                                String.class))
                .containsExactly("ALERT_OPENED", "ALERT_NORMALIZED");
    }

    @Test
    void concurrentReportsNeverCreateTwoActiveAlerts() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks =
                    java.util.stream.IntStream.range(0, 16)
                            .<Callable<Void>>mapToObj(
                                    i ->
                                            () -> {
                                                alerts.report(
                                                        AlertType.STORAGE_FAILED,
                                                        "pdf-storage",
                                                        "WRITE_FAILED");
                                                return null;
                                            })
                            .toList();
            for (Future<Void> done : pool.invokeAll(tasks)) {
                done.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(active(AlertType.STORAGE_FAILED)).hasSize(1);
        assertThat(active(AlertType.STORAGE_FAILED).getFirst().occurrences()).isEqualTo(16);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from authorship_record where operation ="
                                        + " 'ALERT_OPENED'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theDatabaseRefusesTwoActiveAlertsOfTheSameKind() {
        alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into operational_alert (id, type, component,"
                                                + " detail_code, status, started_at, last_seen_at,"
                                                + " occurrences) values (?, 'STORAGE_FAILED',"
                                                + " 'pdf-storage', 'WRITE_FAILED', 'ACTIVE', now(),"
                                                + " now(), 1)",
                                        UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
