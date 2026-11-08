package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.usecase.privacy.ConsentScenario;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Barreira de consentimento: menor sem consentimento válido não chega à IA (FR-034, SC-005). */
class ConsentGuardServiceTest {

    private final ConsentScenario c = new ConsentScenario();
    private final Teacher clara = c.teacher("Clara Souza");

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void minorWithoutConsentIsBlockedWithTheEventOfTheTeacherChannel() {
        Student student = c.minor("Matteo Mafra", clara);
        c.context.define(ActionOrigin.TELEGRAM, "4711");

        assertThatThrownBy(() -> c.guard.requireForAi(clara.id(), student.id()))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));

        assertThat(c.recorder.independent()).hasSize(1);
        AuthorshipRecord event = c.recorder.ofOperation("CONSENT_BLOCKED_AI").getFirst();
        assertThat(event.result()).isEqualTo(AuthorshipResult.BLOCKED);
        assertThat(event.errorCode()).isEqualTo("CONSENT_REQUIRED");
        assertThat(event.origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(event.authorId()).isEqualTo(clara.id());
        assertThat(event.targetId()).isEqualTo(student.id());
    }

    @Test
    void minorWithValidConsentAndAdultsPassWithoutEvents() {
        Student minor = c.minor("Matteo Mafra", clara);
        c.consentTo(minor);
        Student adult = c.adult("Adult Souza", clara);

        c.guard.requireForAi(clara.id(), minor.id());
        c.guard.requireForAi(clara.id(), adult.id());

        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void backgroundProcessingBlocksWithoutAnEventOfTheTeacher() {
        Student student = c.minor("Matteo Mafra", clara);
        c.context.deactivate();

        assertThatThrownBy(() -> c.guard.requireForAi(clara.id(), student.id()))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void secondBarrierRefusesWhenTheConsentWasRevokedAfterTheQueueing() {
        Student student = c.minor("Matteo Mafra", clara);
        c.consentTo(student);
        c.guard.requireForProcessing(student);

        UUID consentId = c.consents.findByStudent(student.id()).getFirst().id();
        c.consents.save(
                c.consents
                        .findById(consentId)
                        .orElseThrow()
                        .revoke(
                                ConsentScenario.TODAY,
                                "MOTHER",
                                UUID.randomUUID(),
                                ConsentScenario.TODAY));

        assertThatThrownBy(() -> c.guard.requireForProcessing(student))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.CONSENT_REQUIRED));
    }

    @Test
    void statusFollowsTheHistory() {
        Student student = c.minor("Matteo Mafra", clara);
        assertThat(c.guard.statusOf(student.id())).isEqualTo(ConsentStatus.MISSING);

        c.consentTo(student);
        assertThat(c.guard.statusOf(student.id())).isEqualTo(ConsentStatus.VALID);

        c.term(2, true);
        assertThat(c.guard.statusOf(student.id())).isEqualTo(ConsentStatus.SUPERSEDED);
    }

    @Test
    void unknownStudentDoesNotBlockBecauseAuthorizationDecidesFirst() {
        assertThat(c.guard.blocks(UUID.randomUUID())).isFalse();
    }
}
