package br.com.felipeblaft.reports.outbound.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.outbound.persistence.JobQueuePostgresAdapter;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Fila persistida no PostgreSQL (R-06): reserva sem duplicar, índice parcial e recuperação. */
class JobQueuePostgresAdapterIT extends IntegrationBase {

    @Autowired private JobQueuePostgresAdapter queue;
    @Autowired private ScenarioIT scenario;

    private Teacher clara;
    private Student matteo;

    @BeforeEach
    void prepare() {
        clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
    }

    private ProcessingJob registration(Student student) {
        return ProcessingJob.create(
                JobType.LESSON_REGISTRATION,
                clara.id(),
                student.id(),
                null,
                null,
                Channel.WEB,
                Medium.AUDIO,
                JobStep.UPLOADING,
                clock.now());
    }

    @Test
    void twoWorkersDoNotReserveSameJob() throws Exception {
        for (int i = 0; i < 12; i++) {
            queue.enqueue(registration(scenario.student(clara, "Aluno " + i, null)));
        }
        Set<UUID> reserved = ConcurrentHashMap.newKeySet();
        List<UUID> all = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        List<Future<?>> executions = new ArrayList<>();
        for (int w = 0; w < 2; w++) {
            executions.add(
                    workers.submit(
                            () -> {
                                start.await();
                                Optional<ProcessingJob> next;
                                while ((next = queue.reserveNext(clock.now())).isPresent()) {
                                    reserved.add(next.get().id());
                                    all.add(next.get().id());
                                }
                                return null;
                            }));
        }
        start.countDown();
        for (Future<?> execution : executions) {
            execution.get(60, TimeUnit.SECONDS);
        }
        workers.shutdown();

        assertThat(all).hasSize(12);
        assertThat(reserved).hasSize(12);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from processing_job where status = 'RUNNING'",
                                Integer.class))
                .isEqualTo(12);
    }

    @Test
    void partialIndexPreventsTwoRegistrationsInProgressForSameStudent() {
        ProcessingJob first = registration(matteo);
        queue.enqueue(first);

        assertThatThrownBy(() -> queue.enqueue(registration(matteo)))
                .isInstanceOf(ConcurrencyConflictException.class);

        first.start(clock.now());
        first.complete(JobResult.AUDIO_NOT_PROCESSABLE, clock.now());
        queue.update(first);
        queue.enqueue(registration(matteo));
        assertThat(jdbc.queryForObject("select count(*) from processing_job", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void stuckJobReturnsToQueueAndWhatDependedOnFileFails() {
        ProcessingJob withoutFile = registration(matteo);
        queue.enqueue(withoutFile);
        ProcessingJob withFile = registration(scenario.student(clara, "Helena Prado", "Violino"));
        withFile.defineTemporaryFile("/tmp/audios/vanished.webm");
        queue.enqueue(withFile);
        queue.reserveNext(clock.now());
        queue.reserveNext(clock.now());

        assertThat(queue.recoverStuck(clock.now())).isEqualTo(2);

        assertThat(queue.searchById(withoutFile.id()).orElseThrow().status())
                .isEqualTo(JobStatus.PENDING);
        ProcessingJob failed = queue.searchById(withFile.id()).orElseThrow();
        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.result()).isEqualTo(JobResult.AUDIO_NOT_PROCESSABLE);
        assertThat(failed.temporaryFile()).isNull();
    }

    @Test
    void delayNoticeIsMarkedOnceAndSurvivesWorkerSave() {
        ProcessingJob job = registration(matteo);
        queue.enqueue(job);
        ProcessingJob doWorker = queue.reserveNext(clock.now()).orElseThrow();

        clock.advance(Duration.ofMinutes(3));
        assertThat(queue.listDelayedWithoutNotice(clock.now().minus(Duration.ofMinutes(3))))
                .hasSize(1);
        assertThat(queue.markDelayNotice(job.id())).isTrue();
        assertThat(queue.markDelayNotice(job.id())).isFalse();

        doWorker.advance(JobStep.TRANSCRIBING);
        queue.update(doWorker);

        assertThat(queue.searchById(job.id()).orElseThrow().delayNoticeSent()).isTrue();
        assertThat(queue.listDelayedWithoutNotice(clock.now())).isEmpty();
    }

    @Test
    void workerSaveDoesNotResurrectDiscardedJobNorEraseStepMessage() {
        ProcessingJob job = registration(matteo);
        queue.enqueue(job);
        ProcessingJob doWorker = queue.reserveNext(clock.now()).orElseThrow();
        queue.registerStepMessage(job.id(), 321);

        doWorker.advance(JobStep.TRANSCRIBING);
        queue.update(doWorker);
        assertThat(queue.searchById(job.id()).orElseThrow().parameter(ProcessingJob.STEP_MESSAGE))
                .contains("321");
        assertThat(doWorker.parameter(ProcessingJob.STEP_MESSAGE)).contains("321");

        ProcessingJob discarded = queue.searchById(job.id()).orElseThrow();
        discarded.discard(clock.now());
        queue.update(discarded);
        doWorker.advance(JobStep.STRUCTURING);
        queue.update(doWorker);

        assertThat(queue.searchById(job.id()).orElseThrow().status())
                .isEqualTo(JobStatus.DISCARDED);
    }
}
