package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.doubles.InMemoryConsentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryConsentTermRepository;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.TaskLlm;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Nenhuma chamada de IA para aluno menor sem consentimento válido (FR-034, SC-005): os seis casos
 * de uso que alcançam a IA recusam com {@code CONSENT_REQUIRED} antes de qualquer porta de
 * transcrição ou de LLM, e a segunda barreira encerra o trabalho enfileirado antes da revogação.
 * Aprovar e cancelar relatório existente continuam permitidos. Os alunos do cenário nasceram em
 * 2012, portanto são menores.
 */
class ConsentBlocksAiTest {

    private final ReportScenario c = new ReportScenario();
    private final InMemoryConsentRepository consents = new InMemoryConsentRepository();
    private final InMemoryConsentTermRepository terms = new InMemoryConsentTermRepository();
    private final ConsentGuardService guard =
            new ConsentGuardService(
                    c.students,
                    consents,
                    terms,
                    c.clock,
                    new AuthorshipService(c.authorshipRecorder, c.clock),
                    c.requestContext);

    private void enableGuard() {
        // o que a preparação do cenário gastou antes da barreira não conta
        c.transcription.received.clear();
        c.llm.inputs.clear();
        c.costs.calls.clear();
        terms.save(
                new ConsentTermVersion(
                        UUID.randomUUID(),
                        1,
                        LocalDate.of(2026, 1, 1),
                        "Institutional text",
                        false,
                        UUID.randomUUID(),
                        c.clock.now()));
        c.register.useConsentGuard(guard);
        c.answer.useConsentGuard(guard);
        c.change.useConsentGuard(guard);
        c.retry.useConsentGuard(guard);
        c.generateSemester.useConsentGuard(guard);
        c.changeSemester.useConsentGuard(guard);
        c.processor.useConsentGuard(guard);
        c.requestContext.define(ActionOrigin.TELEGRAM, "4711");
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    private void assertNoAiTouched() {
        assertThat(c.transcription.received).isEmpty();
        assertThat(c.llm.inputs).isEmpty();
        assertThat(c.costs.calls).isEmpty();
    }

    private SemesterReport semesterInReview() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.llm.schedule(
                TaskLlm.SEMESTER_CONSOLIDATION, ReportScenario.semesterOutput("Bom semestre."));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
        c.processQueue();
        c.llm.inputs.clear();
        c.costs.calls.clear();
        return c.semester.reports.values().iterator().next();
    }

    @Test
    void registeringALessonByAudioIsRefusedBeforeTranscription() {
        enableGuard();

        assertThatThrownBy(
                        () ->
                                c.register.run(
                                        c.clara.id(),
                                        c.matteo.id(),
                                        ReportScenario.file("lesson.ogg", 1000),
                                        Channel.TELEGRAM))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));

        assertNoAiTouched();
        assertThat(c.queue.jobs).isEmpty();
        List<AuthorshipRecord> events = c.authorshipRecorder.ofOperation("CONSENT_BLOCKED_AI");
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().result()).isEqualTo(AuthorshipResult.BLOCKED);
        assertThat(events.getFirst().origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(events.getFirst().targetId()).isEqualTo(c.matteo.id());
    }

    @Test
    void answeringQuestionsByTextAndByAudioIsRefused() {
        LessonReport report = c.reportWithQuestions();
        c.llm.inputs.clear();
        c.costs.calls.clear();
        enableGuard();

        assertThatThrownBy(
                        () ->
                                c.answer.answerText(
                                        c.clara.id(), report.id(), "Escalas", Channel.WEB))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThatThrownBy(
                        () ->
                                c.answer.answerAudio(
                                        c.clara.id(),
                                        report.id(),
                                        ReportScenario.file("answer.ogg", 100),
                                        Channel.WEB))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));

        assertNoAiTouched();
        assertThat(c.authorshipRecorder.ofOperation("CONSENT_BLOCKED_AI")).hasSize(2);
    }

    @Test
    void requestingAChangeByTextAndByAudioIsRefused() {
        LessonReport report = c.reportInReview();
        c.llm.inputs.clear();
        c.costs.calls.clear();
        enableGuard();

        assertThatThrownBy(
                        () ->
                                c.change.changeByText(
                                        c.clara.id(), report.id(), "Mais detalhes", 1, Channel.WEB))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThatThrownBy(
                        () ->
                                c.change.changeByAudio(
                                        c.clara.id(),
                                        report.id(),
                                        ReportScenario.file("change.ogg", 100),
                                        1,
                                        Channel.WEB))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));

        assertNoAiTouched();
    }

    @Test
    void retryingAndGeneratingTheSemesterAreRefused() {
        LessonReport report = c.reportInReview();
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.llm.inputs.clear();
        c.costs.calls.clear();
        enableGuard();

        assertThatThrownBy(() -> c.retry.run(c.clara.id(), report.id(), Channel.WEB))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThatThrownBy(
                        () ->
                                c.generateSemester.run(
                                        c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));

        assertNoAiTouched();
        assertThat(c.semester.reports).isEmpty();
    }

    @Test
    void changingTheSemesterReportIsRefused() {
        SemesterReport report = semesterInReview();
        enableGuard();

        assertThatThrownBy(
                        () ->
                                c.changeSemester.changeByText(
                                        c.clara.id(), report.id(), "Mais detalhes", 1, Channel.WEB))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThatThrownBy(
                        () ->
                                c.changeSemester.changeByAudio(
                                        c.clara.id(),
                                        report.id(),
                                        ReportScenario.file("change.ogg", 100),
                                        1,
                                        Channel.WEB))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));

        assertNoAiTouched();
    }

    @Test
    void approvingAndCancellingAnExistingReportStayAllowed() {
        LessonReport toApprove = c.reportInReview();
        enableGuard();

        c.approve.run(c.clara.id(), toApprove.id(), 1, Channel.WEB);

        assertThat(c.reports.searchById(toApprove.id()).orElseThrow().status().name())
                .isEqualTo("APPROVED");
        assertThat(c.authorshipRecorder.ofOperation("CONSENT_BLOCKED_AI")).isEmpty();
    }

    @Test
    void secondBarrierEndsTheQueuedJobWithoutCallingAnyProvider() {
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("Evoluiu.", List.of()));
        ProcessingJob job =
                c.register.run(
                        c.clara.id(),
                        c.matteo.id(),
                        ReportScenario.file("lesson.ogg", 1000),
                        Channel.WEB);
        // o consentimento é revogado depois de enfileirar e antes de processar
        enableGuard();

        c.processQueue();

        assertThat(c.queue.searchById(job.id()).orElseThrow().result())
                .isEqualTo(JobResult.CONSENT_REQUIRED);
        assertNoAiTouched();
    }

    @Test
    void adultStudentIsNeverBlocked() {
        enableGuard();
        var adult =
                br.com.felipeblaft.reports.domain.registry.Student.create(
                        "Adult Student",
                        LocalDate.of(1990, 1, 1),
                        new byte[] {1},
                        "hash-adult",
                        null,
                        "Piano",
                        c.clock.now());
        c.students.save(adult);
        c.assignments.assign(c.clara.id(), adult.id());
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("Evoluiu.", List.of()));

        c.register.run(
                c.clara.id(), adult.id(), ReportScenario.file("lesson.ogg", 1000), Channel.WEB);
        c.processQueue();

        assertThat(c.llm.inputs).hasSize(1);
    }
}
