package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.TaskLlm;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FR-022: resultado de IA que chega depois da eliminação é descartado e nada é gravado. */
class JobProcessorErasureTest {

    private final ReportScenario c = new ReportScenario();

    @Test
    void jobOfAnErasedStudentIsDiscardedWithoutCallingAnyProvider() {
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("x", List.of()));
        ProcessingJob job =
                c.register.run(
                        c.clara.id(), c.matteo.id(), ReportScenario.file("a.ogg", 10), Channel.WEB);
        c.processor.useErasureCheck(id -> id.equals(c.matteo.id()));

        c.processQueue();

        ProcessingJob stored = c.queue.searchById(job.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(JobStatus.DISCARDED);
        assertThat(stored.result()).isEqualTo(JobResult.DISCARDED);
        assertThat(c.llm.inputs).isEmpty();
        assertThat(c.reports.reports).isEmpty();
        assertThat(c.costs.calls).isEmpty();
    }

    @Test
    void otherStudentsAreNotAffectedByTheCheck() {
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("ok", List.of()));
        c.processor.useErasureCheck(id -> id.equals(c.helena.id()));

        LessonReport report = c.reportInReview();

        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_REVIEW);
    }

    @Test
    void lateAiResultForAnErasedReportIsNotSaved() {
        LessonReport report = c.reportWithQuestions();
        c.llm.schedule(
                TaskLlm.ANSWER_INCORPORATION, ReportScenario.output("Texto novo.", List.of()));
        ProcessingJob job =
                c.answer.answerText(c.clara.id(), report.id(), "Foi o Czerny.", Channel.WEB);
        ProcessingJob running = c.queue.reserveNext(c.clock.now()).orElseThrow();
        int versionsBefore = c.versions.versions.size();
        int interactionsBefore = c.interactions.interactions.size();
        String contentBefore = report.content().progress();
        // a eliminação chega com a IA em andamento: relatório cancelado e anonimizado, trabalho
        // descartado
        report.cancel(c.clock.now(), true);
        report.anonymize(c.clock.now());
        c.reports.save(report);
        running.discard(c.clock.now());
        c.queue.update(running);

        c.processor.process(running);

        assertThat(c.versions.versions).hasSize(versionsBefore);
        assertThat(c.interactions.interactions).hasSize(interactionsBefore);
        assertThat(report.content().progress()).isEqualTo(contentBefore);
        assertThat(report.status()).isEqualTo(ReportStatus.CANCELLED);
        assertThat(c.queue.searchById(job.id()).orElseThrow().status())
                .isEqualTo(JobStatus.DISCARDED);
    }

    @Test
    void anonymizedReportIsNeverWrittenEvenIfStillOpen() {
        LessonReport report = c.reportWithQuestions();
        c.llm.schedule(
                TaskLlm.ANSWER_INCORPORATION, ReportScenario.output("Texto novo.", List.of()));
        c.answer.answerText(c.clara.id(), report.id(), "Foi o Czerny.", Channel.WEB);
        ProcessingJob running = c.queue.reserveNext(c.clock.now()).orElseThrow();
        int versionsBefore = c.versions.versions.size();
        report.anonymize(c.clock.now());
        c.reports.save(report);

        c.processor.process(running);

        assertThat(c.versions.versions).hasSize(versionsBefore);
        assertThat(report.content().progress()).isNotEqualTo("Texto novo.");
        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_ANSWERS);
    }
}
