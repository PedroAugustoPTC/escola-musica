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
                TaskLlm.CONSOLIDACAO_SEMESTRAL, ReportScenario.semesterOutput("Bom semestre."));
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
        code(() -> c.listSemesters.run(c.clara.id(), c.helena.id()), ErrorCode.ALUNO_NAO_ASSOCIADO);
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
                ErrorCode.SEM_AULAS_APROVADAS_NO_PERIODO);
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
                            assertThat(e.code()).isEqualTo(ErrorCode.HA_RELATORIOS_EM_ABERTO);
                            assertThat(e.details()).containsEntry("quantidade", 1);
                        });

        ProcessingJob job =
                c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", true, Channel.WEB);
        assertThat(job.type()).isEqualTo(JobType.CONSOLIDACAO_SEMESTRAL);
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
                ErrorCode.RELATORIO_EM_ABERTO);
    }

    @Test
    void consolidationUsesOnlyApprovedLessonsWithDatesAndWithoutPersonalData() {
        SemesterReport report = semesterInReview();

        SemesterConsolidationInput input = (SemesterConsolidationInput) c.llm.inputs.getFirst();
        assertThat(input.aulas())
                .extracting(a -> a.dataAula())
                .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 5, 20));
        assertThat(input.quantidadeAulas()).isEqualTo(2);
        assertThat(input.toString()).doesNotContain("Matteo").doesNotContain("2012");
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_CONFERENCIA);
        assertThat(report.currentVersion()).isEqualTo(1);
        assertThat(report.consideredLessonCount()).isEqualTo(2);
        assertThat(report.generalInformation().nome()).isEqualTo("Matteo Mafra");
        assertThat(report.generalInformation().professor()).isEqualTo("Clara Souza");
        assertThat(report.content().parecerFinal()).isEqualTo("Bom semestre.");
        assertThat(c.pdf.semesterModels).hasSize(1);
        ProcessingJob job = c.queue.jobs.values().iterator().next();
        assertThat(job.result()).isEqualTo(JobResult.PRONTO_PARA_CONFERENCIA);
        assertThat(job.reportId()).isEqualTo(report.id());
        assertThat(c.notification.events)
                .contains("etapa:CONSOLIDANDO", "resultado:PRONTO_PARA_CONFERENCIA");
    }

    @Test
    void consolidationFailureCreatesNothing() {
        twoApprovedLessonsInSemester1();
        c.llm.schedule(TaskLlm.CONSOLIDACAO_SEMESTRAL, new ProviderUnavailableException("teste"));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);

        c.processQueue();

        assertThat(c.semester.reports).isEmpty();
        assertThat(c.queue.jobs.values().iterator().next().result())
                .isEqualTo(JobResult.FALHA_CONSOLIDACAO);
        // pode gerar de novo
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
    }

    @Test
    void changeGeneratesNewVersionButNoChangeDoesNot() {
        SemesterReport report = semesterInReview();
        c.llm.schedule(
                TaskLlm.REVISAO_SEMESTRAL,
                ReportScenario.semesterOutput("Ótimo semestre."),
                br.com.felipeblaft.reports.port.AiSemesterReportOutput.of(
                        br.com.felipeblaft.reports.domain.semester.SemesterReportContent.empty(),
                        true,
                        "APROVAR"));

        c.changeSemester.changeByText(
                c.clara.id(), report.id(), "Diga que foi ótimo.", 1, Channel.TELEGRAM);
        c.processQueue();
        assertThat(report.currentVersion()).isEqualTo(2);
        assertThat(report.content().parecerFinal()).isEqualTo("Ótimo semestre.");
        assertThat(c.llm.inputs.getLast()).isInstanceOf(SemesterRevisionInput.class);
        assertThat(c.interactions.last(ReportType.SEMESTRAL, report.id()).orElseThrow().result())
                .isEqualTo(InteractionResult.APLICADA);

        c.changeSemester.changeByText(c.clara.id(), report.id(), "aprova aí", 2, Channel.WEB);
        c.processQueue();
        assertThat(report.currentVersion()).isEqualTo(2);
        assertThat(c.queue.jobs.values().stream().reduce((a, b) -> b).orElseThrow().result())
                .isEqualTo(JobResult.PEDIDO_DE_ACAO_IGNORADO);

        code(
                () -> c.changeSemester.changeByText(c.clara.id(), report.id(), "x", 1, Channel.WEB),
                ErrorCode.VERSAO_DESATUALIZADA);
        code(
                () ->
                        c.changeSemester.changeByText(
                                c.clara.id(), report.id(), "  ", 2, Channel.WEB),
                ErrorCode.ALTERACAO_VAZIA);
    }

    @Test
    void approveGeneratesOfficialAndNewGenerationIsRefused() {
        SemesterReport report = semesterInReview();

        Approval approval = c.approveSemester.run(c.clara.id(), report.id(), 1, Channel.WEB);

        assertThat(approval.approvedVersion()).isEqualTo(1);
        assertThat(c.pdf.semesterModels.getLast().status().name()).isEqualTo("OFICIAL");
        ObtainedDocument official =
                c.getDocument.official(c.clara.id(), ReportType.SEMESTRAL, report.id());
        assertThat(official.fileName()).isEqualTo("relatorio-semestral-2026-1.pdf");
        code(
                () ->
                        c.generateSemester.run(
                                c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB),
                ErrorCode.SEMESTRAL_JA_APROVADO);
        code(
                () -> c.approveSemester.run(c.clara.id(), report.id(), 1, Channel.TELEGRAM),
                ErrorCode.RELATORIO_JA_APROVADO);
    }

    @Test
    void cancelReleasesNewGenerationAndOtherTeacherDoesNotSee() {
        SemesterReport report = semesterInReview();
        code(() -> c.getSemester.run(c.bruno.id(), report.id()), ErrorCode.RECURSO_NAO_ENCONTRADO);

        SemesterReportView view = c.getSemester.run(c.clara.id(), report.id());
        assertThat(view.pdfAvailable()).isTrue();
        assertThat(
                        c.getDocument
                                .review(c.clara.id(), ReportType.SEMESTRAL, report.id(), 1)
                                .fileName())
                .isEqualTo("relatorio-semestral-2026-1-v1.pdf");

        c.cancelSemester.cancel(c.clara.id(), report.id());

        assertThat(report.status()).isEqualTo(ReportStatus.CANCELADO);
        c.llm.schedule(TaskLlm.CONSOLIDACAO_SEMESTRAL, ReportScenario.semesterOutput("De novo."));
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
                ErrorCode.RECURSO_NAO_ENCONTRADO);
    }
}
