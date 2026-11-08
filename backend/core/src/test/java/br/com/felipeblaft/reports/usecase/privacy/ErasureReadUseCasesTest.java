package br.com.felipeblaft.reports.usecase.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ErasedStudent;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.privacy.GetErasureReceiptUseCase.Receipt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lista, detalhe e comprovante da eliminação (FR-024, FR-025). */
class ErasureReadUseCasesTest {

    private final ErasureScenario c = new ErasureScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Student student = c.student("Matteo Mafra", clara);

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void receiptHasNoPersonalDataOfTheStudentOrTheRequester() {
        c.lesson(student, clara, ReportStatus.APPROVED, c.clock.now().minusSeconds(60));
        ErasureRequest done = c.erase(student);

        Receipt receipt = c.receipt.run(c.administrator(), done.id(), "req-r");

        assertThat(receipt.protocolReference()).isEqualTo("PROTO-2026/15");
        assertThat(receipt.studentShortId()).matches("^[0-9a-f]{6}$");
        assertThat(receipt.completedAt()).isEqualTo(c.clock.now());
        assertThat(receipt.scope().totalLessonReports()).isEqualTo(1);
        assertThat(receipt.privacyContact()).isEqualTo(ErasureScenario.OFFICER);
        String text = receipt.toString();
        assertThat(text).doesNotContain("Matteo", "Mafra", "Maria Requester");
        assertThat(c.recorder.ofOperation("ERASURE_RECEIPT_READ")).hasSize(1);
    }

    @Test
    void receiptExistsOnlyForCompletedRequests() {
        ErasureRequest open = c.requestFor(student);

        assertThatThrownBy(() -> c.receipt.run(c.administrator(), open.id(), "req"))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> c.receipt.run(c.administrator(), UUID.randomUUID(), "req"))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void teacherCannotReadReceiptOrList() {
        ErasureRequest done = c.erase(student);

        assertThatThrownBy(
                        () -> c.receipt.run(ActorFixtures.teacherWeb(clara.id()), done.id(), "req"))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> c.list.list(ActorFixtures.teacherWeb(clara.id()), null))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> c.list.get(ActorFixtures.teacherWeb(clara.id()), done.id()))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void labelIsTheNameWhileTheStudentExistsAndTheRemovedLabelAfterwards() {
        ErasureRequest request = c.requestFor(student);
        assertThat(c.list.get(c.administrator(), request.id()).studentLabel())
                .isEqualTo("Matteo Mafra");

        c.confirmWithNewPreview(request.id());

        assertThat(c.list.get(c.administrator(), request.id()).studentLabel())
                .isEqualTo(ErasedStudent.label(student.id()))
                .endsWith(student.id().toString().replace("-", "").substring(0, 6).toUpperCase());
    }

    @Test
    void listFiltersByStatusAndShowsTheMostRecentFirst() {
        Student second = c.student("Helena Prado", clara);
        ErasureRequest done = c.erase(student);
        c.clock.advance(java.time.Duration.ofMinutes(1));
        ErasureRequest received = c.requestFor(second);

        assertThat(c.list.list(c.administrator(), null))
                .extracting(view -> view.request().id())
                .containsExactly(received.id(), done.id());
        assertThat(c.list.list(c.administrator(), ErasureStatus.COMPLETED))
                .extracting(view -> view.request().id())
                .containsExactly(done.id());
        assertThat(c.list.list(c.administrator(), ErasureStatus.IN_PROGRESS)).isEmpty();
    }

    @Test
    void unknownRequestIsNotFound() {
        assertThatThrownBy(() -> c.list.get(c.administrator(), UUID.randomUUID()))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
