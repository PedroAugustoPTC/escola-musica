package br.com.felipeblaft.reports.usecase.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.privacy.ErasureConfirmation;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.privacy.ConfirmErasureUseCase.Command;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Confirmar a eliminação: token, palavra, alcance completo e retomada (FR-021 a FR-026). */
class ConfirmErasureUseCaseTest {

    private final ErasureScenario c = new ErasureScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Teacher bruno = c.teacher("Bruno Lima");
    private final Student student = c.student("Matteo Mafra", clara, bruno);
    private final Instant approvedAt = c.clock.now().minusSeconds(3600);

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    private String token(ErasureRequest request) {
        return c.preview.run(c.administrator(), request.id(), "req-p").confirmationToken();
    }

    private Command command(ErasureRequest request, String token, String word) {
        return new Command(request.id(), token, word, "req-c");
    }

    @Test
    void wrongWordIsAValidationErrorAndNothingChanges() {
        c.lesson(student, clara, ReportStatus.APPROVED, approvedAt);
        ErasureRequest request = c.requestFor(student);
        String token = token(request);

        assertThatThrownBy(() -> c.confirm.run(c.administrator(), command(request, token, "erase")))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThat(c.students.searchById(student.id())).isPresent();
        assertThat(c.erasures.findById(request.id()).orElseThrow().status())
                .isEqualTo(ErasureStatus.RECEIVED);
        assertThat(c.lessons.reports.values()).allMatch(r -> r.anonymizedAt() == null);
    }

    @Test
    void expiredTokenIsRefusedAndNothingChanges() {
        c.lesson(student, clara, ReportStatus.APPROVED, approvedAt);
        ErasureRequest request = c.requestFor(student);
        String token = token(request);
        c.clock.advance(Duration.ofMinutes(11));

        assertThatThrownBy(
                        () ->
                                c.confirm.run(
                                        c.administrator(),
                                        command(
                                                request,
                                                token,
                                                ErasureConfirmation.CONFIRMATION_WORD)))
                .satisfies(
                        error ->
                                assertThat(codeOf(error))
                                        .isEqualTo(ErrorCode.CONFIRMATION_EXPIRED));

        assertThat(c.students.searchById(student.id())).isPresent();
        assertThat(c.lessons.reports.values()).allMatch(r -> r.anonymizedAt() == null);
    }

    @Test
    void missingPreviewMeansNoValidToken() {
        ErasureRequest request = c.requestFor(student);

        assertThatThrownBy(
                        () ->
                                c.confirm.run(
                                        c.administrator(),
                                        command(
                                                request,
                                                "made-up-token",
                                                ErasureConfirmation.CONFIRMATION_WORD)))
                .satisfies(
                        error ->
                                assertThat(codeOf(error))
                                        .isEqualTo(ErrorCode.CONFIRMATION_EXPIRED));
    }

    @Test
    void usedTokenCannotRunTheErasureTwice() {
        ErasureRequest request = c.requestFor(student);
        String token = token(request);
        c.confirm.run(
                c.administrator(), command(request, token, ErasureConfirmation.CONFIRMATION_WORD));

        assertThatThrownBy(
                        () ->
                                c.confirm.run(
                                        c.administrator(),
                                        command(
                                                request,
                                                token,
                                                ErasureConfirmation.CONFIRMATION_WORD)))
                .satisfies(
                        error ->
                                assertThat(codeOf(error))
                                        .isEqualTo(ErrorCode.ERASURE_ALREADY_COMPLETED));
    }

    @Test
    void erasesEverythingOfEveryTeacherIncludingApprovedReports() {
        LessonReport approved = c.lesson(student, clara, ReportStatus.APPROVED, approvedAt);
        LessonReport otherTeacher = c.lesson(student, bruno, ReportStatus.APPROVED, approvedAt);
        LessonReport open = c.lesson(student, clara, ReportStatus.AWAITING_REVIEW, null);
        LessonReport cancelled = c.lesson(student, clara, ReportStatus.CANCELLED, null);
        SemesterReport semester = c.semester(student, clara, approvedAt);
        PdfDocument lessonPdf = c.pdf(ReportType.LESSON, approved.id());
        PdfDocument semesterPdf = c.pdf(ReportType.SEMESTER, semester.id());
        GuardianConsent consent = c.consent(student);
        Student other = c.student("Helena Prado", clara);
        LessonReport others = c.lesson(other, clara, ReportStatus.APPROVED, approvedAt);

        ErasureRequest done = c.erase(student);

        assertThat(done.status()).isEqualTo(ErasureStatus.COMPLETED);
        assertThat(done.requesterName()).isNull();
        assertThat(done.scope().orElseThrow().totalLessonReports()).isEqualTo(4);
        assertThat(done.scope().orElseThrow().pdfs()).isEqualTo(2);
        assertThat(c.students.wasErased(student.id())).isTrue();
        assertThat(c.students.searchById(student.id())).isEmpty();
        assertThat(c.erasures.isErased(student.id())).isTrue();
        assertThat(c.assignments.listTeachersOfStudent(student.id())).isEmpty();
        for (LessonReport report : new LessonReport[] {approved, otherTeacher, open, cancelled}) {
            LessonReport stored = c.lessons.reports.get(report.id());
            assertThat(stored.anonymizedAt()).isNotNull();
            assertThat(stored.transcription()).isNull();
        }
        assertThat(c.lessons.reports.get(open.id()).status()).isEqualTo(ReportStatus.CANCELLED);
        assertThat(c.lessons.reports.get(approved.id()).status()).isEqualTo(ReportStatus.APPROVED);
        assertThat(c.semesters.reports.get(semester.id()).anonymizedAt()).isNotNull();
        assertThat(c.interactions.interactions)
                .filteredOn(i -> !i.reportId().equals(others.id()))
                .allMatch(i -> i.text() == null && i.textRemovedAt() != null);
        assertThat(c.pdfs.documents.values()).allMatch(d -> d.contentRemovedAt() != null);
        assertThat(c.storage.files)
                .doesNotContainKeys(lessonPdf.storageKey(), semesterPdf.storageKey());
        assertThat(c.consents.findById(consent.id()).orElseThrow().consentingName()).isNull();
        assertThat(c.consents.findByStudent(student.id())).hasSize(1);
        // outro aluno da escola não é tocado
        assertThat(c.students.searchById(other.id())).isPresent();
        assertThat(c.lessons.reports.get(others.id()).anonymizedAt()).isNull();
        assertThat(c.lessons.reports.get(others.id()).transcription()).isEqualTo("source-text");
    }

    @Test
    void recordsConfirmationAndCompletionInTheAuthorshipTrail() {
        c.lesson(student, clara, ReportStatus.APPROVED, approvedAt);

        ErasureRequest done = c.erase(student);

        assertThat(c.recorder.ofOperation("ERASURE_CONFIRM")).hasSize(1);
        var completed = c.recorder.ofOperation("ERASURE_COMPLETED");
        assertThat(completed).hasSize(1);
        assertThat(completed.getFirst().targetId()).isEqualTo(done.id());
        assertThat(completed.getFirst().toString()).doesNotContain("Maria Requester");
    }

    @Test
    void teacherCannotConfirm() {
        ErasureRequest request = c.requestFor(student);
        String token = token(request);

        assertThatThrownBy(
                        () ->
                                c.confirm.run(
                                        ActorFixtures.teacherWeb(clara.id()),
                                        command(
                                                request,
                                                token,
                                                ErasureConfirmation.CONFIRMATION_WORD)))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(c.students.searchById(student.id())).isPresent();
    }

    @Test
    void storageFailureLeavesTheRequestInProgressWithAlertAndANewConfirmationFinishes() {
        LessonReport approved = c.lesson(student, clara, ReportStatus.APPROVED, approvedAt);
        PdfDocument pdf = c.pdf(ReportType.LESSON, approved.id());
        c.storage.failingDeletes.add(pdf.storageKey());

        ErasureRequest partial = c.erase(student);

        assertThat(partial.status()).isEqualTo(ErasureStatus.IN_PROGRESS);
        assertThat(partial.requesterName()).isEqualTo("Maria Requester");
        assertThat(c.students.wasErased(student.id())).isTrue();
        assertThat(c.alerts.reported())
                .extracting(r -> r.type())
                .contains(AlertType.STORAGE_FAILED);
        assertThat(c.recorder.ofOperation("ERASURE_COMPLETED")).isEmpty();

        c.storage.failingDeletes.clear();
        ErasureRequest done = c.confirmWithNewPreview(partial.id());

        assertThat(done.status()).isEqualTo(ErasureStatus.COMPLETED);
        assertThat(done.requesterName()).isNull();
        assertThat(done.scope()).isEqualTo(partial.scope());
        assertThat(c.storage.files).doesNotContainKey(pdf.storageKey());
        assertThat(c.recorder.ofOperation("ERASURE_COMPLETED")).hasSize(1);
    }

    @Test
    void retentionFinishesPendingRemovalsOnItsOwn() {
        LessonReport approved = c.lesson(student, clara, ReportStatus.APPROVED, approvedAt);
        PdfDocument pdf = c.pdf(ReportType.LESSON, approved.id());
        c.storage.failingDeletes.add(pdf.storageKey());
        ErasureRequest partial = c.erase(student);

        assertThat(c.confirm.resumePending(c.retention(), "req-r1")).isZero();
        c.storage.failingDeletes.clear();
        assertThat(c.confirm.resumePending(c.retention(), "req-r2")).isEqualTo(1);

        ErasureRequest done = c.erasures.findById(partial.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(ErasureStatus.COMPLETED);
        assertThat(done.requesterName()).isNull();
    }

    @Test
    void teacherCannotResumePending() {
        assertThatThrownBy(
                        () -> c.confirm.resumePending(ActorFixtures.teacherWeb(clara.id()), "req"))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
