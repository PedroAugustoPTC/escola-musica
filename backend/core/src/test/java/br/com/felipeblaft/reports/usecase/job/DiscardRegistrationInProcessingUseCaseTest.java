package br.com.felipeblaft.reports.usecase.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.TemporaryFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DiscardRegistrationInProcessingUseCaseTest {

    private final ReportScenario c = new ReportScenario();

    private ProcessingJob register() {
        TemporaryFile file = ReportScenario.file("aula.webm", 2048);
        return c.register.run(c.clara.id(), c.matteo.id(), file, Channel.WEB);
    }

    @Test
    void pendingJobIsDiscardedAndAudioDeleted() {
        ProcessingJob job = register();

        c.discardRegistration.run(c.clara.id(), job.id());

        ProcessingJob discarded = c.queue.searchById(job.id()).orElseThrow();
        assertThat(discarded.status()).isEqualTo(JobStatus.DESCARTADO);
        assertThat(discarded.temporaryFile()).isNull();
        assertThat(c.normalization.deleted).contains(Path.of("/tmp/audios/aula.webm"));
        c.processQueue();
        assertThat(c.reports.reports.values()).isEmpty();
        // o aluno fica livre para um novo registro
        assertThat(register().status()).isEqualTo(JobStatus.PENDENTE);
    }

    @Test
    void workerResultAfterDiscardIsIgnored() {
        ProcessingJob job = register();
        ProcessingJob running = c.queue.reserveNext(c.clock.now()).orElseThrow();

        c.discardRegistration.run(c.clara.id(), job.id());
        c.processor.process(running);

        assertThat(c.reports.reports.values()).isEmpty();
        assertThat(c.queue.searchById(job.id()).orElseThrow().status())
                .isEqualTo(JobStatus.DESCARTADO);
        assertThat(c.notification.events).noneMatch(e -> e.startsWith("resultado:"));
    }

    @Test
    void otherTeacherReceivesNotFound() {
        ProcessingJob job = register();

        assertThatThrownBy(() -> c.discardRegistration.run(c.bruno.id(), job.id()))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void completedJobOrJobWithReportCannotBeDiscarded() {
        LessonReport report = c.reportWithQuestions();
        ProcessingJob completed =
                c.queue.jobs.values().stream()
                        .filter(t -> report.id().equals(t.reportId()))
                        .findFirst()
                        .orElseThrow();

        assertThatThrownBy(() -> c.discardRegistration.run(c.clara.id(), completed.id()))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ACAO_NAO_PERMITIDA_NA_ETAPA));
    }
}
