package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.PASSWORD;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InactivateAdministratorUseCaseTest {

    private final AdministratorScenario c = new AdministratorScenario();
    private final InactivateAdministratorUseCase useCase =
            new InactivateAdministratorUseCase(
                    c.authorization, c.administrators, c.revocation, c.authorship, c.clock);
    private final ValidateAdministratorSessionUseCase validate =
            new ValidateAdministratorSessionUseCase(c.sessions, c.protection, c.clock);
    private final Administrator ana = c.activated("Ana Souza", "ana@school.test");
    private final Administrator bia = c.activated("Bia Lima", "bia@school.test");
    private final Actor actorAna = ActorFixtures.administratorWeb(ana.id());

    private AdministratorView inactivate(Actor actor, UUID id) {
        return useCase.run(actor, new InactivateAdministratorUseCase.Command(id, CORRELATION));
    }

    @Test
    void inactivatesTheOtherAdministratorErasingThePasswordAndTheCode() {
        AdministratorView view = inactivate(actorAna, bia.id());

        Administrator saved = c.administrators.searchById(bia.id()).orElseThrow();
        assertThat(saved.active()).isFalse();
        assertThat(saved.passwordHash()).isNull();
        assertThat(saved.activationCodeHash()).isNull();
        assertThat(view.id()).isEqualTo(bia.id());
        assertThat(view.active()).isFalse();
        assertThat(view.name()).isEqualTo("Bia Lima");
    }

    @Test
    void endsAllTheSessionsOfTheInactivatedOneRightAway() {
        AdministratorSessionResult first = c.signInAs(bia, PASSWORD);
        AdministratorSessionResult second = c.signInAs(bia, PASSWORD);
        AdministratorSessionResult mine = c.signInAs(ana, PASSWORD);

        inactivate(actorAna, bia.id());

        failWith(() -> validate.run(first.sessionToken()), ErrorCode.ACCESS_ENDED);
        failWith(() -> validate.run(second.sessionToken()), ErrorCode.ACCESS_ENDED);
        assertThat(validate.run(mine.sessionToken()).actor().id()).isEqualTo(ana.id());
        assertThat(c.sessions.ofAdministrator(bia.id()))
                .allSatisfy(
                        session ->
                                assertThat(session.revocationReason())
                                        .isEqualTo(SessionRevocationReason.ACCOUNT_INACTIVATED));
    }

    @Test
    void theInactivatedOneCannotSignInAgain() {
        inactivate(actorAna, bia.id());

        failWith(() -> c.signInAs(bia, PASSWORD), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void aPendingAdministratorCanBeInactivatedAndLosesTheCode() {
        Administrator caio = c.pending("Caio Dias", "caio@school.test", "KQMXPTRWHNCV");

        inactivate(actorAna, caio.id());

        Administrator saved = c.administrators.searchById(caio.id()).orElseThrow();
        assertThat(saved.active()).isFalse();
        assertThat(saved.activationCodeHash()).isNull();
    }

    @Test
    void theLastActiveAdministratorCannotBeInactivatedNotEvenByItself() {
        c.administrators.save(bia.inactivate(c.clock.now()));
        AdministratorSessionResult mine = c.signInAs(ana, PASSWORD);
        c.recorder.rollBackTransaction();

        failWith(() -> inactivate(actorAna, ana.id()), ErrorCode.LAST_ADMINISTRATOR);

        assertThat(c.administrators.searchById(ana.id()).orElseThrow()).isEqualTo(ana);
        assertThat(validate.run(mine.sessionToken())).isNotNull();
        assertThat(c.recorder.records()).isEmpty();
    }

    @Test
    void anAdministratorMayInactivateItselfWhenAnotherOneIsActive() {
        AdministratorSessionResult mine = c.signInAs(ana, PASSWORD);

        inactivate(actorAna, ana.id());

        assertThat(c.administrators.searchById(ana.id()).orElseThrow().active()).isFalse();
        failWith(() -> validate.run(mine.sessionToken()), ErrorCode.ACCESS_ENDED);
    }

    @Test
    void theSecondOfTwoSerializedInactivationsIsRefused() {
        inactivate(actorAna, bia.id());

        failWith(() -> inactivate(actorAna, ana.id()), ErrorCode.LAST_ADMINISTRATOR);

        assertThat(c.administrators.lockAllActiveForUpdate()).hasSize(1);
    }

    @Test
    void theActiveOnesAreLockedBeforeDeciding() {
        inactivate(actorAna, bia.id());

        assertThat(c.administrators.locks()).isEqualTo(1);
    }

    @Test
    void inactivatingAnAlreadyInactiveOneChangesNothingAndWritesNothing() {
        inactivate(actorAna, bia.id());
        int records = c.recorder.records().size();
        int recordings = c.administrators.recordings();

        AdministratorView view = inactivate(actorAna, bia.id());

        assertThat(view.active()).isFalse();
        assertThat(c.recorder.records()).hasSize(records);
        assertThat(c.administrators.recordings()).isEqualTo(recordings);
    }

    @Test
    void anUnknownAdministratorIsNotFound() {
        failWith(() -> inactivate(actorAna, UUID.randomUUID()), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void writesTheInactivationAuthorshipInTheSameTransaction() {
        inactivate(actorAna, bia.id());

        AuthorshipRecord record = c.recorder.ofOperation("ADMINISTRATOR_INACTIVATE").getFirst();
        assertThat(c.recorder.inTransaction()).contains(record);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.authorId()).isEqualTo(ana.id());
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.ADMINISTRATOR);
        assertThat(record.targetId()).isEqualTo(bia.id());
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
    }

    @Test
    void onlyAnAdministratorMayInactivate() {
        failWith(
                () -> inactivate(ActorFixtures.teacherWeb(), bia.id()),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> inactivate(ActorFixtures.teacherTelegram(), bia.id()),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(c.administrators.searchById(bia.id()).orElseThrow().active()).isTrue();
    }

    @Test
    void anAdministratorWithAProvisionalPasswordCannotInactivate() {
        failWith(
                () -> inactivate(ActorFixtures.administratorWithProvisionalPassword(), bia.id()),
                ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }
}
