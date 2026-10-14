package br.com.felipeblaft.reports.usecase.semester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.InteractionResult;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.SemesterConsolidationInput;
import br.com.felipeblaft.reports.port.SemesterRevisionInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase.Approval;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase.Preview;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase.SemesterReportView;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase.Semester;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Casos de uso e consolidação do semestral (US4, data-model.md §9). */
class SemesterUseCasesTest {

    private final ReportScenario c = new ReportScenario();

    private static void code(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }

    private void twoApprovedLessonsInSemester1() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        c.approvedLesson(c.matteo, LocalDate.of(2026, 5, 20), "Estudo nº 5");
    }

    /** Gera, processa e devolve o semestral de Matteo no 1º semestre de 2026. */
    private SemesterReport semesterInReview() {
        twoApprovedLessonsInSemester1();
        c.llm.schedule(
                TaskLlm.SEMESTER_CONSOLIDATION, ReportScenario.semesterOutput("Bom semestre."));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
        c.processQueue();
        return c.semester.reports.values().iterator().next();
    }

    @Test
    void semestersSinceFirstLessonWithMostRecentPreselected() {
        c.approvedLesson(c.matteo, LocalDate.of(2025, 11, 3), "Escala");

        List<Semester> semesters = c.listSemesters.run(c.clara.id(), c.matteo.id());

        assertThat(semesters)
                .extracting(s -> s.period().key())
                .containsExactly("2026-2", "2026-1", "2025-2");
        assertThat(semesters.getFirst().inProgress()).isTrue();
        assertThat(semesters.getFirst().preSelected()).isTrue();
        assertThat(semesters.get(1).preSelected()).isFalse();
        code(
                () -> c.listSemesters.run(c.clara.id(), c.helena.id()),
                ErrorCode.STUDENT_NOT_ASSIGNED);
    }

    @Test
    void previewCountsApprovedAndOpenOfPeriod() {
        twoApprovedLessonsInSemester1();
        c.approvedLesson(c.matteo, LocalDate.of(2026, 8, 1), "Fora do período");

        Preview preview = c.preview.run(c.clara.id(), c.matteo.id(), "2026-1");

        assertThat(preview.approvedLessons()).isEqualTo(2);
        assertThat(preview.openLessonReports()).isZero();
        assertThat(preview.approvedSemesterId()).isEmpty();
        assertThat(preview.openSemester()).isEmpty();
    }

    @Test
    void withoutApprovedLessonsNothingIsCreatedAndAiIsNotCalled() {
        code(
                () ->
                        c.generateSemester.run(
                                c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB),
                ErrorCode.NO_APPROVED_LESSONS_IN_PERIOD);
        assertThat(c.queue.jobs).isEmpty();
        assertThat(c.llm.inputs).isEmpty();
    }

    @Test
    void openLessonReportsRequireConfirmation() {
        twoApprovedLessonsInSemester1();
        LessonReport open = c.reportWithQuestions();
        // a aula em aberto é de hoje (2º semestre): leva-se uma do 1º para dentro do período
        c.reports.lessons.put(
                open.lessonId(),
                new br.com.felipeblaft.reports.domain.report.Lesson(
                        open.lessonId(),
                        c.clara.id(),
                        c.matteo.id(),
                        LocalDate.of(2026, 4, 1),
                        c.clock.now()));

        assertThatThrownBy(
                        () ->
                                c.generateSemester.run(
                                        c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo(ErrorCode.OPEN_REPORTS_EXIST);
                            assertThat(e.details()).containsEntry("quantity", 1);
                        });

        ProcessingJob job =
                c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", true, Channel.WEB);
        assertThat(job.type()).isEqualTo(JobType.SEMESTER_CONSOLIDATION);
        assertThat(job.parameter(ProcessingJob.PERIOD_KEY)).contains("2026-1");
    }

    @Test
    void consolidationInProgressCountsAsOpen() {
        twoApprovedLessonsInSemester1();
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.TELEGRAM);

        assertThat(c.preview.run(c.clara.id(), c.matteo.id(), "2026-1").openSemester())
                .hasValueSatisfying(a -> assertThat(a.originChannel()).isEqualTo(Channel.TELEGRAM));
        code(
                () ->
                        c.generateSemester.run(
                                c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB),
                ErrorCode.REPORT_ALREADY_OPEN);
    }

    @Test
    void consolidationUsesOnlyApprovedLessonsWithDatesAndWithoutPersonalData() {
        SemesterReport report = semesterInReview();

        SemesterConsolidationInput input = (SemesterConsolidationInput) c.llm.inputs.getFirst();
        assertThat(input.lessons())
                .extracting(a -> a.lessonDate())
                .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 5, 20));
        assertThat(input.lessonCount()).isEqualTo(2);
        assertThat(input.toString()).doesNotContain("Matteo").doesNotContain("2012");
        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_REVIEW);
        assertThat(report.currentVersion()).isEqualTo(1);
        assertThat(report.consideredLessonCount()).isEqualTo(2);
        assertThat(report.generalInformation().name()).isEqualTo("Matteo Mafra");
        assertThat(report.generalInformation().teacher()).isEqualTo("Clara Souza");
        assertThat(report.content().finalOpinion()).isEqualTo("Bom semestre.");
        assertThat(c.pdf.semesterModels).hasSize(1);
        ProcessingJob job = c.queue.jobs.values().iterator().next();
        assertThat(job.result()).isEqualTo(JobResult.READY_FOR_REVIEW);
        assertThat(job.reportId()).isEqualTo(report.id());
        assertThat(c.notification.events).contains("step:CONSOLIDATING", "result:READY_FOR_REVIEW");
    }

    @Test
    void consolidationFailureCreatesNothing() {
        twoApprovedLessonsInSemester1();
        c.llm.schedule(TaskLlm.SEMESTER_CONSOLIDATION, new ProviderUnavailableException("test"));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);

        c.processQueue();

        assertThat(c.semester.reports).isEmpty();
        assertThat(c.queue.jobs.values().iterator().next().result())
                .isEqualTo(JobResult.CONSOLIDATION_FAILED);
        // pode gerar de novo
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
    }

    @Test
    void changeGeneratesNewVersionButNoChangeDoesNot() {
        SemesterReport report = semesterInReview();
        c.llm.schedule(
                TaskLlm.SEMESTER_REVISION,
                ReportScenario.semesterOutput("Ótimo semestre."),
                br.com.felipeblaft.reports.port.AiSemesterReportOutput.of(
                        br.com.felipeblaft.reports.domain.semester.SemesterReportContent.empty(),
                        true,
                        "APPROVE"));

        c.changeSemester.changeByText(
                c.clara.id(), report.id(), "Diga que foi ótimo.", 1, Channel.TELEGRAM);
        c.processQueue();
        assertThat(report.currentVersion()).isEqualTo(2);
        assertThat(report.content().finalOpinion()).isEqualTo("Ótimo semestre.");
        assertThat(c.llm.inputs.getLast()).isInstanceOf(SemesterRevisionInput.class);
        assertThat(c.interactions.last(ReportType.SEMESTER, report.id()).orElseThrow().result())
                .isEqualTo(InteractionResult.APPLIED);

        c.changeSemester.changeByText(c.clara.id(), report.id(), "aprova aí", 2, Channel.WEB);
        c.processQueue();
        assertThat(report.currentVersion()).isEqualTo(2);
        assertThat(c.queue.jobs.values().stream().reduce((a, b) -> b).orElseThrow().result())
                .isEqualTo(JobResult.ACTION_REQUEST_IGNORED);

        code(
                () -> c.changeSemester.changeByText(c.clara.id(), report.id(), "x", 1, Channel.WEB),
                ErrorCode.STALE_VERSION);
        code(
                () ->
                        c.changeSemester.changeByText(
                                c.clara.id(), report.id(), "  ", 2, Channel.WEB),
                ErrorCode.EMPTY_CHANGE);
    }

    @Test
    void approveGeneratesOfficialAndNewGenerationIsRefused() {
        SemesterReport report = semesterInReview();

        Approval approval = c.approveSemester.run(c.clara.id(), report.id(), 1, Channel.WEB);

        assertThat(approval.approvedVersion()).isEqualTo(1);
        assertThat(c.pdf.semesterModels.getLast().status().name()).isEqualTo("OFFICIAL");
        ObtainedDocument official =
                c.getDocument.official(c.clara.id(), ReportType.SEMESTER, report.id());
        assertThat(official.fileName()).isEqualTo("relatorio-semestral-2026-1.pdf");
        code(
                () ->
                        c.generateSemester.run(
                                c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB),
                ErrorCode.SEMESTER_REPORT_ALREADY_APPROVED);
        code(
                () -> c.approveSemester.run(c.clara.id(), report.id(), 1, Channel.TELEGRAM),
                ErrorCode.REPORT_ALREADY_APPROVED);
    }

    @Test
    void cancelReleasesNewGenerationAndOtherTeacherDoesNotSee() {
        SemesterReport report = semesterInReview();
        code(() -> c.getSemester.run(c.bruno.id(), report.id()), ErrorCode.RESOURCE_NOT_FOUND);

        SemesterReportView view = c.getSemester.run(c.clara.id(), report.id());
        assertThat(view.pdfAvailable()).isTrue();
        assertThat(
                        c.getDocument
                                .review(c.clara.id(), ReportType.SEMESTER, report.id(), 1)
                                .fileName())
                .isEqualTo("relatorio-semestral-2026-1-v1.pdf");

        c.cancelSemester.cancel(c.clara.id(), report.id());

        assertThat(report.status()).isEqualTo(ReportStatus.CANCELLED);
        c.llm.schedule(TaskLlm.SEMESTER_CONSOLIDATION, ReportScenario.semesterOutput("De novo."));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
        c.processQueue();
        assertThat(c.semester.reports).hasSize(2);
    }

    @Test
    void discardConsolidationInProgressIgnoresResult() {
        twoApprovedLessonsInSemester1();
        ProcessingJob job =
                c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);

        c.cancelSemester.discardConsolidation(c.clara.id(), job.id());
        c.processQueue();

        assertThat(c.semester.reports).isEmpty();
        code(
                () -> c.cancelSemester.discardConsolidation(c.bruno.id(), job.id()),
                ErrorCode.RESOURCE_NOT_FOUND);
    }
}
