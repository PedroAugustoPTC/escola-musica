package br.com.felipeblaft.reports.usecase.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.privacy.ErasureConfirmation;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.privacy.PreviewErasureUseCase.Preview;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prévia: alcance só em números, token novo a cada prévia e validade de 10 minutos (FR-021). */
class PreviewErasureUseCaseTest {

    private final ErasureScenario c = new ErasureScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Teacher bruno = c.teacher("Bruno Lima");
    private final Student student = c.student("Matteo Mafra", clara, bruno);

    private ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void scopeCountsReportsByStatusPdfsTeachersAndConsents() {
        LessonReport approved =
                c.lesson(student, clara, ReportStatus.APPROVED, c.clock.now().minusSeconds(3600));
        c.lesson(student, bruno, ReportStatus.AWAITING_REVIEW, null);
        c.lesson(student, clara, ReportStatus.CANCELLED, null);
        c.semester(student, clara, c.clock.now().minusSeconds(3600));
        c.pdf(ReportType.LESSON, approved.id());
        c.pdf(ReportType.LESSON, approved.id());
        c.consent(student);
        ErasureRequest request = c.requestFor(student);

        Preview preview = c.preview.run(c.administrator(), request.id(), "req-p");

        assertThat(preview.scope().lessonReports())
                .containsEntry("APPROVED", 1)
                .containsEntry("AWAITING_REVIEW", 1)
                .containsEntry("CANCELLED", 1);
        assertThat(preview.scope().semesterReports()).containsEntry("APPROVED", 1);
        assertThat(preview.scope().pdfs()).isEqualTo(2);
        assertThat(preview.scope().assignments()).isEqualTo(2);
        assertThat(preview.scope().consents()).isEqualTo(1);
        assertThat(preview.teachers())
                .extracting(t -> t.name())
                .containsExactlyInAnyOrder("Clara Souza", "Bruno Lima");
        assertThat(preview.privacyContact()).isEqualTo(ErasureScenario.OFFICER);
        assertThat(preview.expiresAt()).isEqualTo(c.clock.now().plus(ErasureConfirmation.VALIDITY));
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() {
        ErasureRequest request = c.requestFor(student);

        Preview preview = c.preview.run(c.administrator(), request.id(), "req-p");

        ErasureRequest stored = c.erasures.findById(request.id()).orElseThrow();
        String hash = stored.confirmation().orElseThrow().tokenHash();
        assertThat(hash).isEqualTo(c.protection.sessionTokenHash(preview.confirmationToken()));
        assertThat(hash).isNotEqualTo(preview.confirmationToken());
        assertThat(stored.toString()).doesNotContain(preview.confirmationToken());
        assertThat(preview.toString()).doesNotContain(preview.confirmationToken());
    }

    @Test
    void eachPreviewIssuesANewTokenAndInvalidatesTheOldOne() {
        ErasureRequest request = c.requestFor(student);
        Preview first = c.preview.run(c.administrator(), request.id(), "req-p1");

        Preview second = c.preview.run(c.administrator(), request.id(), "req-p2");

        assertThat(second.confirmationToken()).isNotEqualTo(first.confirmationToken());
        assertThatThrownBy(
                        () ->
                                c.confirm.run(
                                        c.administrator(),
                                        new ConfirmErasureUseCase.Command(
                                                request.id(),
                                                first.confirmationToken(),
                                                ErasureConfirmation.CONFIRMATION_WORD,
                                                "req-c")))
                .satisfies(
                        error ->
                                assertThat(codeOf(error))
                                        .isEqualTo(ErrorCode.CONFIRMATION_EXPIRED));
    }

    @Test
    void previewDoesNotChangeAnyData() {
        c.lesson(student, clara, ReportStatus.AWAITING_REVIEW, null);
        ErasureRequest request = c.requestFor(student);

        c.preview.run(c.administrator(), request.id(), "req-p");

        assertThat(c.students.searchById(student.id())).isPresent();
        assertThat(c.assignments.listTeachersOfStudent(student.id())).hasSize(2);
        assertThat(c.lessons.reports.values()).allMatch(r -> r.anonymizedAt() == null);
        assertThat(c.recorder.ofOperation("ERASURE_PREVIEW")).hasSize(1);
    }

    @Test
    void teacherCannotPreviewAndUnknownRequestIsNotFound() {
        ErasureRequest request = c.requestFor(student);

        assertThatThrownBy(
                        () ->
                                c.preview.run(
                                        ActorFixtures.teacherWeb(clara.id()), request.id(), "req"))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> c.preview.run(c.administrator(), UUID.randomUUID(), "req"))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void completedRequestCannotBePreviewedAgain() {
        ErasureRequest done = c.erase(student);
        assertThat(done.status().name()).isEqualTo("COMPLETED");

        assertThatThrownBy(() -> c.preview.run(c.administrator(), done.id(), "req"))
                .satisfies(
                        error ->
                                assertThat(codeOf(error))
                                        .isEqualTo(ErrorCode.ERASURE_ALREADY_COMPLETED));
        assertThat(Duration.ofMinutes(10)).isEqualTo(ErasureConfirmation.VALIDITY);
    }
}
