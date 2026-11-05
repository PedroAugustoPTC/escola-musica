package br.com.felipeblaft.reports.usecase.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.privacy.RegisterErasureRequestUseCase.Command;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Registrar o pedido de eliminação: só o administrador; nada é apagado ainda (FR-020). */
class RegisterErasureRequestUseCaseTest {

    private final ErasureScenario c = new ErasureScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Student student = c.student("Matteo Mafra", clara);

    private Command command(UUID studentId) {
        return new Command(
                studentId,
                "MOTHER",
                "Maria Requester",
                LocalDate.of(2026, 9, 15),
                "IN_PERSON",
                "PROTO-2026/15",
                "req-1");
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void administratorRegistersAndTheRequestStartsReceived() {
        ErasureRequest request = c.register.run(c.administrator(), command(student.id()));

        assertThat(request.status()).isEqualTo(ErasureStatus.RECEIVED);
        assertThat(request.requesterName()).isEqualTo("Maria Requester");
        assertThat(c.erasures.findById(request.id())).contains(request);
        assertThat(c.students.searchById(student.id())).isPresent();
        var event = c.recorder.ofOperation("ERASURE_REQUEST_REGISTER").getFirst();
        assertThat(event.targetType()).isEqualTo(AuthorshipTargetType.ERASURE_REQUEST);
        assertThat(event.targetId()).isEqualTo(request.id());
        assertThat(c.recorder.inTransaction()).contains(event);
    }

    @Test
    void teacherCannotRegisterAndNothingIsSaved() {
        assertThatThrownBy(
                        () ->
                                c.register.run(
                                        ActorFixtures.teacherWeb(clara.id()),
                                        command(student.id())))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(c.erasures.listAll()).isEmpty();
    }

    @Test
    void unknownStudentIsNotFound() {
        assertThatThrownBy(() -> c.register.run(c.administrator(), command(UUID.randomUUID())))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void secondOpenRequestForTheSameStudentIsRefused() {
        c.register.run(c.administrator(), command(student.id()));

        assertThatThrownBy(() -> c.register.run(c.administrator(), command(student.id())))
                .satisfies(
                        error ->
                                assertThat(codeOf(error))
                                        .isEqualTo(ErrorCode.ERASURE_ALREADY_OPEN));
        assertThat(c.erasures.listAll()).hasSize(1);
    }

    @Test
    void erasedStudentIsRefusedWithItsOwnCode() {
        c.erase(student);

        assertThatThrownBy(() -> c.register.run(c.administrator(), command(student.id())))
                .satisfies(
                        error ->
                                assertThat(codeOf(error))
                                        .isEqualTo(ErrorCode.STUDENT_ALREADY_ERASED));
    }

    @Test
    void invalidProtocolOrFutureDateIsAValidationError() {
        Command badProtocol =
                new Command(
                        student.id(),
                        "MOTHER",
                        "Maria Requester",
                        LocalDate.of(2026, 9, 15),
                        "IN_PERSON",
                        "has spaces",
                        "req-1");
        Command futureDate =
                new Command(
                        student.id(),
                        "MOTHER",
                        "Maria Requester",
                        LocalDate.of(2026, 9, 22),
                        "IN_PERSON",
                        "PROTO-1",
                        "req-1");

        assertThatThrownBy(() -> c.register.run(c.administrator(), badProtocol))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> c.register.run(c.administrator(), futureDate))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(c.erasures.listAll()).isEmpty();
    }

    @Test
    void commandToStringNeverShowsTheRequesterName() {
        assertThat(command(student.id()).toString()).doesNotContain("Maria");
    }
}
