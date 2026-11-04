package br.com.felipeblaft.reports.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.OfficialDocumentService;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import br.com.felipeblaft.reports.usecase.document.GetSchoolReportDocumentUseCase.Command;
import br.com.felipeblaft.reports.usecase.document.GetSchoolReportDocumentUseCase.Disposition;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PDF oficial da versão aprovada, entregue à escola (US2). */
class GetSchoolReportDocumentUseCaseTest {

    private static final String CORRELATION = "req-2";

    private final ReportScenario c = new ReportScenario();
    private final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
    private final GetSchoolReportDocumentUseCase document =
            new GetSchoolReportDocumentUseCase(
                    new ActorAuthorizationService(PermissionMatrix.standard()),
                    c.reports,
                    c.semester,
                    new OfficialDocumentService(c.versions, c.pdfDocuments, c.documents),
                    new AuthorshipService(recorder, c.clock),
                    c.transaction);
    private final Actor admin = ActorFixtures.administratorWeb();

    private static Command command(ReportType type, UUID id, Disposition disposition) {
        return new Command(type, id, disposition, CORRELATION);
    }

    private LessonReport approveLesson(LessonReport review) {
        c.approve.run(c.clara.id(), review.id(), review.currentVersion(), Channel.WEB);
        return c.reports.searchById(review.id()).orElseThrow();
    }

    private SemesterReport approvedSemester() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.llm.schedule(TaskLlm.SEMESTER_CONSOLIDATION, ReportScenario.semesterOutput("Bom."));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
        c.processQueue();
        SemesterReport semester = c.semester.reports.values().iterator().next();
        c.approveSemester.run(c.clara.id(), semester.id(), 1, Channel.WEB);
        return c.semester.searchById(semester.id()).orElseThrow();
    }

    private static void assertCode(Throwable error, ErrorCode code) {
        assertThat(error)
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void administratorGetsTheOfficialPdfOfAnyTeachersLessonSameAsTheTeacher() {
        LessonReport report = approveLesson(c.reportInReview());

        ObtainedDocument school =
                document.run(admin, command(ReportType.LESSON, report.id(), Disposition.OPEN));
        ObtainedDocument teacher =
                c.getDocument.official(c.clara.id(), ReportType.LESSON, report.id());

        assertThat(school.content()).isNotEmpty().isEqualTo(teacher.content());
        assertThat(school.fileName()).isEqualTo(teacher.fileName());
    }

    @Test
    void administratorGetsTheOfficialPdfOfTheSemesterReport() {
        SemesterReport report = approvedSemester();

        ObtainedDocument school =
                document.run(
                        admin, command(ReportType.SEMESTER, report.id(), Disposition.DOWNLOAD));
        ObtainedDocument teacher =
                c.getDocument.official(c.clara.id(), ReportType.SEMESTER, report.id());

        assertThat(school.content()).isEqualTo(teacher.content());
        assertThat(school.fileName()).isEqualTo(teacher.fileName());
    }

    @Test
    void notApprovedAndMissingReportsAreIndistinguishable() {
        LessonReport inReview = c.reportInReview();
        assertThat(inReview.status()).isNotEqualTo(ReportStatus.APPROVED);

        Throwable notApproved =
                org.assertj.core.api.Assertions.catchThrowable(
                        () ->
                                document.run(
                                        admin,
                                        command(
                                                ReportType.LESSON,
                                                inReview.id(),
                                                Disposition.OPEN)));
        Throwable missing =
                org.assertj.core.api.Assertions.catchThrowable(
                        () ->
                                document.run(
                                        admin,
                                        command(
                                                ReportType.LESSON,
                                                UUID.randomUUID(),
                                                Disposition.OPEN)));
        Throwable missingSemester =
                org.assertj.core.api.Assertions.catchThrowable(
                        () ->
                                document.run(
                                        admin,
                                        command(
                                                ReportType.SEMESTER,
                                                UUID.randomUUID(),
                                                Disposition.DOWNLOAD)));

        assertCode(notApproved, ErrorCode.RESOURCE_NOT_FOUND);
        assertCode(missing, ErrorCode.RESOURCE_NOT_FOUND);
        assertCode(missingSemester, ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void awaitingAnswersReportIsNotFound() {
        LessonReport awaiting = c.reportWithQuestions();

        assertThatThrownBy(
                        () ->
                                document.run(
                                        admin,
                                        command(
                                                ReportType.LESSON,
                                                awaiting.id(),
                                                Disposition.OPEN)))
                .satisfies(e -> assertCode(e, ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void teacherOnAnyChannelGetsResourceNotFoundEvenForOwnApprovedReport() {
        LessonReport report = approveLesson(c.reportInReview());
        recorder.rollBackTransaction();

        for (Actor teacher :
                new Actor[] {
                    ActorFixtures.teacherWeb(c.clara.id()),
                    ActorFixtures.teacherTelegram(c.clara.id())
                }) {
            assertThatThrownBy(
                            () ->
                                    document.run(
                                            teacher,
                                            command(
                                                    ReportType.LESSON,
                                                    report.id(),
                                                    Disposition.DOWNLOAD)))
                    .satisfies(e -> assertCode(e, ErrorCode.RESOURCE_NOT_FOUND));
        }
        assertThat(recorder.isEmpty()).isTrue();
    }

    @Test
    void provisionalPasswordIsRefused() {
        LessonReport report = approveLesson(c.reportInReview());

        assertThatThrownBy(
                        () ->
                                document.run(
                                        ActorFixtures.administratorWithProvisionalPassword(),
                                        command(ReportType.LESSON, report.id(), Disposition.OPEN)))
                .satisfies(e -> assertCode(e, ErrorCode.PASSWORD_CHANGE_REQUIRED));
        assertThat(recorder.isEmpty()).isTrue();
    }

    @Test
    void unavailableApprovedPdfIsInternalErrorAndRecorded() {
        LessonReport report = approveLesson(c.reportInReview());
        c.storage.files.clear();

        assertThatThrownBy(
                        () ->
                                document.run(
                                        admin,
                                        command(ReportType.LESSON, report.id(), Disposition.OPEN)))
                .satisfies(e -> assertCode(e, ErrorCode.INTERNAL_ERROR));

        AuthorshipRecord record = recorder.independent().getLast();
        assertThat(record.operation()).isEqualTo(Operation.SCHOOL_REPORT_DOCUMENT_OPEN.name());
        assertThat(record.result()).isEqualTo(AuthorshipResult.FAILURE);
        assertThat(record.errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.LESSON_REPORT);
        assertThat(record.targetId()).isEqualTo(report.id());
    }

    @Test
    void successRecordsOpenOrDownloadWithTargetAndDocumentVersion() {
        LessonReport lesson = approveLesson(c.reportInReview());
        SemesterReport semester = approvedSemester();

        document.run(admin, command(ReportType.LESSON, lesson.id(), Disposition.OPEN));
        document.run(admin, command(ReportType.SEMESTER, semester.id(), Disposition.DOWNLOAD));

        AuthorshipRecord open =
                recorder.ofOperation(Operation.SCHOOL_REPORT_DOCUMENT_OPEN).getFirst();
        assertThat(open.targetType()).isEqualTo(AuthorshipTargetType.LESSON_REPORT);
        assertThat(open.targetId()).isEqualTo(lesson.id());
        assertThat(open.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(open.readDetail().documentVersion()).isEqualTo(lesson.approvedVersion());
        assertThat(recorder.inTransaction()).contains(open);

        AuthorshipRecord download =
                recorder.ofOperation(Operation.SCHOOL_REPORT_DOCUMENT_DOWNLOAD).getFirst();
        assertThat(download.targetType()).isEqualTo(AuthorshipTargetType.SEMESTER_REPORT);
        assertThat(download.targetId()).isEqualTo(semester.id());
        assertThat(download.readDetail().documentVersion()).isEqualTo(semester.approvedVersion());
    }

    @Test
    void refusalIsRecordedInAnIndependentTransactionWithTheRequestedId() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                document.run(
                                        admin,
                                        command(
                                                ReportType.SEMESTER,
                                                missing,
                                                Disposition.DOWNLOAD)))
                .satisfies(e -> assertCode(e, ErrorCode.RESOURCE_NOT_FOUND));

        AuthorshipRecord record = recorder.independent().getFirst();
        assertThat(record.operation()).isEqualTo(Operation.SCHOOL_REPORT_DOCUMENT_DOWNLOAD.name());
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.SEMESTER_REPORT);
        assertThat(record.targetId()).isEqualTo(missing);
        assertThat(record.result()).isEqualTo(AuthorshipResult.FAILURE);
        assertThat(record.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(record.readDetail()).isNull();
    }

    @Test
    void failureToRecordSuccessDeliversNothing() {
        LessonReport report = approveLesson(c.reportInReview());
        recorder.failNextRecordWith(new IllegalStateException("falha simulada"));

        assertThatThrownBy(
                        () ->
                                document.run(
                                        admin,
                                        command(ReportType.LESSON, report.id(), Disposition.OPEN)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noReportChanges() {
        LessonReport report = approveLesson(c.reportInReview());
        int version = report.approvedVersion();
        var approvedAt = report.approvedAt();

        document.run(admin, command(ReportType.LESSON, report.id(), Disposition.DOWNLOAD));

        LessonReport after = c.reports.searchById(report.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(ReportStatus.APPROVED);
        assertThat(after.approvedVersion()).isEqualTo(version);
        assertThat(after.approvedAt()).isEqualTo(approvedAt);
    }
}
