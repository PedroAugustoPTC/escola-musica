package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cancelar apaga o cadastro em andamento (CPF cifrado inclusive) e não grava nada. */
class CancelStudentRegistrationUseCaseTest {

    private final DraftScenario scenario = new DraftScenario();

    private void cancel(UUID linkId) {
        scenario.cancel().run(scenario.actor, new CancelStudentRegistrationUseCase.Command(linkId));
    }

    @Test
    void cancelErasesTheDraftAndWritesNothing() {
        StudentRegistrationDraft draft = scenario.confirmableCreate("ABCD2345");
        assertThat(draft.cpfEncrypted()).isNotNull();

        cancel(scenario.link.id());

        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isEmpty();
        assertThat(scenario.drafts.all()).isEmpty();
        assertThat(scenario.writes()).isZero();
        assertThat(scenario.students.all()).isEmpty();
        assertThat(scenario.decryption.decryptions()).isZero();
    }

    @Test
    void cancelWorksForEveryKind() {
        UUID student = scenario.ownStudent().id();
        for (Kind kind : Kind.values()) {
            scenario.startDraft(kind, kind == Kind.CREATE ? null : student);

            cancel(scenario.link.id());

            assertThat(scenario.drafts.all()).isEmpty();
        }
    }

    @Test
    void cancelWithoutADraftIsHarmless() {
        cancel(scenario.link.id());

        assertThat(scenario.drafts.removals()).isZero();
    }

    @Test
    void cancelNeverTouchesAnotherLinksDraft() {
        scenario.confirmableCreate("ABCD2345");

        cancel(UUID.randomUUID());

        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isPresent();
    }

    @Test
    void aTokenAlreadyShownStopsWorkingAfterCancel() {
        scenario.confirmableCreate("ABCD2345");
        cancel(scenario.link.id());

        try {
            scenario.confirm()
                    .run(
                            scenario.actor,
                            new ConfirmStudentRegistrationUseCase.Command(
                                    scenario.link.id(), "ABCD2345", "update-1"));
            throw new AssertionError("esperava recusa");
        } catch (DomainException error) {
            assertThat(error.code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);
        }
        assertThat(scenario.writes()).isZero();
    }

    @Test
    void theEndOfTheLinkAlsoErasesTheDraft() {
        scenario.confirmableCreate("ABCD2345");

        scenario.links.remove(scenario.link.id());

        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isEmpty();
    }

    @Test
    void onlyTheTeacherOnTelegramMayCancel() {
        scenario.confirmableCreate("ABCD2345");
        for (Actor denied :
                new Actor[] {
                    ActorFixtures.administratorWeb(),
                    Actor.teacher(scenario.teacher.id(), ActionOrigin.WEB),
                    ActorFixtures.initialLoad()
                }) {
            try {
                scenario.cancel()
                        .run(
                                denied,
                                new CancelStudentRegistrationUseCase.Command(scenario.link.id()));
                throw new AssertionError("esperava recusa");
            } catch (DomainException error) {
                assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            }
        }
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isPresent();
    }
}
