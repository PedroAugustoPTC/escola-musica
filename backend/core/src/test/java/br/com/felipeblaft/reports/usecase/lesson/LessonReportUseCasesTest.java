package br.com.felipeblaft.reports.usecase.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.PdfGenerationPort.DocumentStatus;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.job.QueryJobUseCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * Responder, alterar, tentar de novo, aprovar, cancelar, obter relatório, consultar trabalho e
 * obter documento.
 */
class LessonReportUseCasesTest {

    private final ReportScenario c = new ReportScenario();

    private static void failWith(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }

    @Test
    void answerRequiresTextAndRightStep() {
        LessonReport report = c.reportWithQuestions();
        failWith(
                () -> c.answer.answerText(c.clara.id(), report.id(), "  ", Channel.WEB),
                ErrorCode.EMPTY_ANSWER);
        failWith(
                () -> c.answer.answerText(c.bruno.id(), report.id(), "x", Channel.WEB),
                ErrorCode.RESOURCE_NOT_FOUND);
        c.answer.answerText(c.clara.id(), report.id(), "response", Channel.WEB);
        assertThat(report.step()).isEqualTo(ReportStep.INCORPORATING_ANSWERS);
        failWith(
                () -> c.answer.answerText(c.clara.id(), report.id(), "de novo", Channel.WEB),
                ErrorCode.PROCESSING_IN_PROGRESS);
        failWith(
                () ->
                        c.answer.answerAudio(
                                c.clara.id(),
                                report.id(),
                                ReportScenario.file("r.ogg", 1),
                                Channel.WEB),
                ErrorCode.PROCESSING_IN_PROGRESS);
        assertThat(c.normalization.deleted).contains(Path.of("/tmp/audios/r.ogg"));
    }

    @Test
    void answerAudioAboveLimit() {
        LessonReport report = c.reportWithQuestions();
        failWith(
                () ->
                        c.answer.answerAudio(
                                c.clara.id(),
                                report.id(),
                                new ChannelFileReference(
                                        "f", null, null, c.parameters.audioLimitBytes() + 1),
                                Channel.TELEGRAM),
                ErrorCode.AUDIO_TOO_LARGE);
        assertThat(report.step()).isNull();
    }

    @Test
    void changeValidatesVersionTextAndCrossAccess() {
        LessonReport report = c.reportInReview();
        failWith(
                () -> c.change.changeByText(c.clara.id(), report.id(), " ", 1, Channel.WEB),
                ErrorCode.EMPTY_CHANGE);
        assertThatThrownBy(
                        () -> c.change.changeByText(c.clara.id(), report.id(), "x", 3, Channel.WEB))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e ->
                                assertThat(((DomainException) e).details())
                                        .containsEntry("currentVersion", 1));
        failWith(
                () -> c.change.changeByText(c.bruno.id(), report.id(), "x", 1, Channel.WEB),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () ->
                        c.change.changeByAudio(
                                c.clara.id(),
                                report.id(),
                                ReportScenario.file("a.ogg", 1),
                                2,
                                Channel.WEB),
                ErrorCode.STALE_VERSION);
        assertThat(c.normalization.deleted).contains(Path.of("/tmp/audios/a.ogg"));
        ProcessingJob job =
                c.change.changeByAudio(
                        c.clara.id(), report.id(), ReportScenario.file("b.ogg", 1), 1, Channel.WEB);
        assertThat(job.parameters()).containsEntry("baseVersion", "1");
        assertThat(report.step()).isEqualTo(ReportStep.TRANSCRIBING_REQUEST);
    }

    @Test
    void approveGeneratesOfficialPdfAndDoubleApprovalIsRefused() {
        LessonReport report = c.reportInReview();
        c.clock.advance(Duration.ofMinutes(5));

        ApproveLessonReportUseCase.Approval approval =
                c.approve.run(c.clara.id(), report.id(), 1, Channel.TELEGRAM);

        assertThat(approval.approvedVersion()).isEqualTo(1);
        assertThat(approval.approvedAt()).isEqualTo(c.clock.now());
        assertThat(report.approvalChannel()).isEqualTo(Channel.TELEGRAM);
        assertThat(c.pdf.models.getLast().status()).isEqualTo(DocumentStatus.OFFICIAL);
        assertThat(c.pdf.models.getLast().approval().teacherName()).isEqualTo("Clara Souza");
        assertThat(c.storage.files).containsKey("reports/lesson/" + report.id() + "/official.pdf");
        failWith(
                () -> c.approve.run(c.clara.id(), report.id(), 1, Channel.WEB),
                ErrorCode.REPORT_ALREADY_APPROVED);
        failWith(
                () -> c.cancel.cancel(c.clara.id(), report.id()),
                ErrorCode.REPORT_ALREADY_APPROVED);
    }

    @Test
    void approveStaleVersionOrOtherTeachers() {
        LessonReport report = c.reportInReview();
        failWith(
                () -> c.approve.run(c.clara.id(), report.id(), 2, Channel.WEB),
                ErrorCode.STALE_VERSION);
        failWith(
                () -> c.approve.run(c.bruno.id(), report.id(), 1, Channel.WEB),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> c.approve.run(c.clara.id(), UUID.randomUUID(), 1, Channel.WEB),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void cancelDiscardsJobInProgress() {
        LessonReport report = c.reportInReview();
        ProcessingJob job =
                c.change.changeByText(c.clara.id(), report.id(), "change", 1, Channel.WEB);
        report.completeStep(c.clock.now());

        c.cancel.cancel(c.clara.id(), report.id());

        assertThat(report.status()).isEqualTo(ReportStatus.CANCELLED);
        assertThat(c.queue.searchById(job.id()).orElseThrow().status())
                .isEqualTo(JobStatus.DISCARDED);
        failWith(() -> c.cancel.cancel(c.clara.id(), report.id()), ErrorCode.REPORT_CANCELLED);
    }

    @Test
    void retryOnlyOnFailure() {
        LessonReport report = c.reportInReview();
        failWith(
                () -> c.retry.run(c.clara.id(), report.id(), Channel.WEB),
                ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
    }

    @Test
    void getReportWithInteractionJobAndPdf() {
        LessonReport withQuestions = c.reportWithQuestionsOfOtherStudent();
        LessonReport report = c.reportInReview();
        GetLessonReportUseCase.LessonReportView view = c.get.run(c.clara.id(), report.id());
        assertThat(view.studentName()).isEqualTo("Matteo Mafra");
        assertThat(view.teacherName()).isEqualTo("Clara Souza");
        assertThat(view.pdfAvailable()).isTrue();
        assertThat(view.lastInteraction()).isEmpty();
        assertThat(view.jobInProgress()).isEmpty();

        ProcessingJob job = c.change.changeByText(c.clara.id(), report.id(), "x", 1, Channel.WEB);
        assertThat(c.get.run(c.clara.id(), report.id()).jobInProgress()).contains(job);
        failWith(() -> c.get.run(c.bruno.id(), report.id()), ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(c.get.run(c.clara.id(), withQuestions.id()).pdfAvailable()).isFalse();
    }

    @Test
    void queryJobShowsDelayAndTextOnlyToOwner() {
        LessonReport report = c.reportInReview();
        ProcessingJob job =
                c.change.changeByText(c.clara.id(), report.id(), "texto digitado", 1, Channel.WEB);
        QueryJobUseCase.JobView view = c.queryJob.run(c.clara.id(), job.id());
        assertThat(view.delayed()).isFalse();
        assertThat(view.understoodText()).contains("texto digitado");
        c.clock.advance(Duration.ofMinutes(3));
        assertThat(c.queryJob.run(c.clara.id(), job.id()).delayed()).isTrue();
        failWith(() -> c.queryJob.run(c.bruno.id(), job.id()), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void queryJobIsDeniedAfterTheAssignmentOfTheStudentIsRemoved() {
        LessonReport report = c.reportInReview();
        ProcessingJob job =
                c.change.changeByText(c.clara.id(), report.id(), "texto digitado", 1, Channel.WEB);
        assertThat(c.queryJob.run(c.clara.id(), job.id()).understoodText()).isPresent();

        c.assignments.delete(c.clara.id(), c.matteo.id());

        failWith(() -> c.queryJob.run(c.clara.id(), job.id()), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void reviewAndOfficialDocuments() {
        LessonReport report = c.reportInReview();
        GetReportDocumentUseCase.ObtainedDocument review =
                c.getDocument.review(c.clara.id(), ReportType.LESSON, report.id(), 1);
        assertThat(new String(review.content(), StandardCharsets.UTF_8)).isEqualTo("PDF REVIEW v1");
        assertThat(review.fileName())
                .isEqualTo("relatorio-aula-2026-09-21-v1.pdf")
                .doesNotContain(report.id().toString());

        failWith(
                () -> c.getDocument.official(c.clara.id(), ReportType.LESSON, report.id()),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> c.getDocument.review(c.clara.id(), ReportType.LESSON, report.id(), 2),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> c.getDocument.review(c.bruno.id(), ReportType.LESSON, report.id(), 1),
                ErrorCode.RESOURCE_NOT_FOUND);

        c.approve.run(c.clara.id(), report.id(), 1, Channel.WEB);
        GetReportDocumentUseCase.ObtainedDocument official =
                c.getDocument.official(c.clara.id(), ReportType.LESSON, report.id());
        assertThat(new String(official.content(), StandardCharsets.UTF_8))
                .isEqualTo("PDF OFFICIAL v1");
        assertThat(official.fileName()).isEqualTo("relatorio-aula-2026-09-21.pdf");
        assertThat(c.pdfDocuments.documents.values())
                .extracting(d -> d.type())
                .containsExactlyInAnyOrder(DocumentType.REVIEW, DocumentType.OFFICIAL);

        failWith(
                () -> c.getDocument.official(c.clara.id(), ReportType.SEMESTER, report.id()),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> c.getDocument.review(c.clara.id(), ReportType.SEMESTER, report.id(), 1),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void registeredSemesterSource() {
        var obtained =
                new GetReportDocumentUseCase.ObtainedDocument(new byte[] {1}, "semester.pdf");
        c.getDocument.registerSemesterSource(
                new GetReportDocumentUseCase.DocumentSource() {
                    @Override
                    public ReportType type() {
                        return ReportType.SEMESTER;
                    }

                    @Override
                    public GetReportDocumentUseCase.ObtainedDocument review(UUID p, UUID r, int n) {
                        return obtained;
                    }

                    @Override
                    public GetReportDocumentUseCase.ObtainedDocument official(UUID p, UUID r) {
                        return obtained;
                    }
                });
        assertThat(c.getDocument.official(c.clara.id(), ReportType.SEMESTER, UUID.randomUUID()))
                .isSameAs(obtained);
        assertThat(c.getDocument.review(c.clara.id(), ReportType.SEMESTER, UUID.randomUUID(), 1))
                .isSameAs(obtained);
        assertThat(List.of()).isEmpty();
        assertThat(TaskLlm.values()).isNotEmpty();
    }
}
