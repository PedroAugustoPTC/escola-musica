package br.com.felipeblaft.reports.usecase.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.privacy.RevokeConsentUseCase.Command;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Revogar consentimento: só o administrador; o registro fica e a IA passa a ser bloqueada. */
class RevokeConsentUseCaseTest {

    private final ConsentScenario c = new ConsentScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Student student = c.minor("Matteo Mafra", clara);

    private UUID recorded() {
        c.consentTo(student);
        return c.consents.findByStudent(student.id()).getFirst().id();
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void revocationKeepsTheRecordAndBlocksAi() {
        UUID consentId = recorded();
        assertThat(c.guard.blocks(student.id())).isFalse();

        ConsentView view =
                c.revoke.run(
                        c.administrator(),
                        new Command(consentId, LocalDate.of(2026, 9, 10), "MOTHER", "req-1"));

        assertThat(view.status()).isEqualTo(ConsentStatus.REVOKED);
        assertThat(c.consents.findByStudent(student.id())).hasSize(1);
        assertThat(c.consents.findById(consentId).orElseThrow().revokedBy()).isEqualTo("MOTHER");
        assertThat(c.guard.blocks(student.id())).isTrue();
        var event = c.recorder.ofOperation("CONSENT_REVOKE").getFirst();
        assertThat(event.targetType()).isEqualTo(AuthorshipTargetType.CONSENT);
        assertThat(event.targetId()).isEqualTo(consentId);
        assertThat(c.recorder.inTransaction()).contains(event);
    }

    @Test
    void revokingAgainIsRefused() {
        UUID consentId = recorded();
        Command command = new Command(consentId, LocalDate.of(2026, 9, 10), "MOTHER", "req-1");
        c.revoke.run(c.administrator(), command);

        assertThatThrownBy(() -> c.revoke.run(c.administrator(), command))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_ALREADY_REVOKED));
    }

    @Test
    void teacherCannotRevokeAndTheStateDoesNotChange() {
        UUID consentId = recorded();

        assertThatThrownBy(
                        () ->
                                c.revoke.run(
                                        ActorFixtures.teacherWeb(clara.id()),
                                        new Command(consentId, LocalDate.of(2026, 9, 10), "MOTHER", "req-1")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(c.consents.findById(consentId).orElseThrow().revoked()).isFalse();
    }

    @Test
    void unknownConsentIsNotFound() {
        assertThatThrownBy(
                        () ->
                                c.revoke.run(
                                        c.administrator(),
                                        new Command(UUID.randomUUID(), LocalDate.of(2026, 9, 10), "MOTHER", "req-1")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void futureDateOrUnknownRevokerIsAValidationError() {
        UUID consentId = recorded();

        assertThatThrownBy(
                        () ->
                                c.revoke.run(
                                        c.administrator(),
                                        new Command(consentId, LocalDate.of(2026, 9, 22), "NEIGHBOR", "req-1")))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(c.consents.findById(consentId).orElseThrow().revoked()).isFalse();
    }

    @Test
    void newRecordAfterTheRevocationLiftsTheBlock() {
        UUID consentId = recorded();
        c.revoke.run(
                c.administrator(),
                new Command(consentId, LocalDate.of(2026, 9, 10), "MOTHER", "req-1"));
        assertThat(c.guard.blocks(student.id())).isTrue();

        c.consentTo(student);

        assertThat(c.guard.blocks(student.id())).isFalse();
    }
}
