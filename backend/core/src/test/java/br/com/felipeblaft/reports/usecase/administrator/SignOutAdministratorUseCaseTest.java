package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.PASSWORD;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import org.junit.jupiter.api.Test;

class SignOutAdministratorUseCaseTest {

    private final AdministratorScenario c = new AdministratorScenario();
    private final SignOutAdministratorUseCase useCase =
            new SignOutAdministratorUseCase(
                    c.authorization, c.sessions, c.protection, c.authorship);
    private final Administrator ana = c.activated("Ana Souza", "ana@school.test");

    private SignOutAdministratorUseCase.Command command(Secret token) {
        return new SignOutAdministratorUseCase.Command(token, CORRELATION);
    }

    @Test
    void signingOutDeletesOnlyThatSessionAndKeepsTheOthersOfTheSameAdministrator() {
        AdministratorSessionResult first = c.signInAs(ana, PASSWORD);
        AdministratorSessionResult second = c.signInAs(ana, PASSWORD);

        useCase.run(ActorFixtures.administratorWeb(ana.id()), command(first.sessionToken()));

        assertThat(c.sessions.all()).hasSize(1);
        assertThat(c.sessions.all().getFirst().tokenHash())
                .isEqualTo(c.protection.sessionTokenHash(second.sessionToken().value()));
    }

    @Test
    void writesTheSignOutAuthorshipInTheSameTransaction() {
        AdministratorSessionResult opened = c.signInAs(ana, PASSWORD);
        c.recorder.rollBackTransaction();

        useCase.run(ActorFixtures.administratorWeb(ana.id()), command(opened.sessionToken()));

        AuthorshipRecord record = c.recorder.inTransaction().getFirst();
        assertThat(record.operation()).isEqualTo("ADMIN_SIGN_OUT");
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.authorId()).isEqualTo(ana.id());
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.ADMINISTRATOR);
        assertThat(record.targetId()).isEqualTo(ana.id());
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(record.originHash()).isNull();
    }

    @Test
    void theRestrictedActorMayStillSignOut() {
        Administrator first = c.provisional("Bia Lima", "bia@school.test", "provisional password");
        AdministratorSessionResult opened = c.signInAs(first, "provisional password");

        useCase.run(Actor.administrator(first.id(), true), command(opened.sessionToken()));

        assertThat(c.sessions.all()).isEmpty();
    }

    @Test
    void aTeacherCannotSignOutAnAdministratorSession() {
        AdministratorSessionResult opened = c.signInAs(ana, PASSWORD);

        failWith(
                () -> useCase.run(ActorFixtures.teacherWeb(), command(opened.sessionToken())),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(c.sessions.all()).hasSize(1);
    }

    @Test
    void aSessionOfAnotherAdministratorIsNeitherDeletedNorRecorded() {
        Administrator bia = c.activated("Bia Lima", "bia@school.test");
        AdministratorSessionResult biaSession = c.signInAs(bia, PASSWORD);
        c.recorder.rollBackTransaction();

        failWith(
                () ->
                        useCase.run(
                                ActorFixtures.administratorWeb(ana.id()),
                                command(biaSession.sessionToken())),
                ErrorCode.NOT_AUTHENTICATED);

        assertThat(c.sessions.all()).hasSize(1);
        assertThat(c.recorder.records()).isEmpty();
    }

    @Test
    void anUnknownOrMissingTokenIsNotAuthenticated() {
        Actor actor = ActorFixtures.administratorWeb(ana.id());

        failWith(
                () -> useCase.run(actor, command(new Secret("unknown"))),
                ErrorCode.NOT_AUTHENTICATED);
        failWith(() -> useCase.run(actor, command(null)), ErrorCode.NOT_AUTHENTICATED);
    }

    @Test
    void aBlankTokenIsNotAuthenticated() {
        failWith(
                () ->
                        useCase.run(
                                ActorFixtures.administratorWeb(ana.id()), command(new Secret(" "))),
                ErrorCode.NOT_AUTHENTICATED);
    }

    @Test
    void afterSigningOutTheTokenNoLongerValidates() {
        AdministratorSessionResult opened = c.signInAs(ana, PASSWORD);
        ValidateAdministratorSessionUseCase validate =
                new ValidateAdministratorSessionUseCase(c.sessions, c.protection, c.clock);
        assertThat(validate.run(opened.sessionToken())).isNotNull();

        useCase.run(ActorFixtures.administratorWeb(ana.id()), command(opened.sessionToken()));

        failWith(() -> validate.run(opened.sessionToken()), ErrorCode.NOT_AUTHENTICATED);
    }
}
