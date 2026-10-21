package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.PASSWORD;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import org.junit.jupiter.api.Test;

class GetAdministratorSessionUseCaseTest {

    private final AdministratorScenario c = new AdministratorScenario();
    private final GetAdministratorSessionUseCase useCase =
            new GetAdministratorSessionUseCase(
                    c.authorization, c.sessions, c.administrators, c.protection);
    private final Administrator ana = c.activated("Ana Souza", "ana@school.test");

    private GetAdministratorSessionUseCase.Command command(Secret token) {
        return new GetAdministratorSessionUseCase.Command(token);
    }

    @Test
    void returnsTheNameTheFixedExpiryAndWhetherThePasswordMustChange() {
        AdministratorSessionResult opened = c.signInAs(ana, PASSWORD);

        GetAdministratorSessionUseCase.Result result =
                useCase.run(
                        ActorFixtures.administratorWeb(ana.id()), command(opened.sessionToken()));

        assertThat(result.administratorId()).isEqualTo(ana.id());
        assertThat(result.name()).isEqualTo("Ana Souza");
        assertThat(result.expiresAt()).isEqualTo(opened.expiresAt());
        assertThat(result.mustChangePassword()).isFalse();
        assertThat(result.toString()).doesNotContain("Ana");
    }

    @Test
    void theRestrictedActorMayReadItsSession() {
        Administrator first = c.provisional("Bia Lima", "bia@school.test", "provisional password");
        AdministratorSessionResult opened = c.signInAs(first, "provisional password");

        GetAdministratorSessionUseCase.Result result =
                useCase.run(Actor.administrator(first.id(), true), command(opened.sessionToken()));

        assertThat(result.mustChangePassword()).isTrue();
    }

    @Test
    void aTeacherGetsTheSameAnswerAsAMissingRoute() {
        AdministratorSessionResult opened = c.signInAs(ana, PASSWORD);

        failWith(
                () -> useCase.run(ActorFixtures.teacherWeb(), command(opened.sessionToken())),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> useCase.run(ActorFixtures.teacherTelegram(), command(opened.sessionToken())),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void aSessionOfAnotherAdministratorIsNotAuthenticated() {
        Administrator bia = c.activated("Bia Lima", "bia@school.test");
        AdministratorSessionResult opened = c.signInAs(bia, PASSWORD);

        failWith(
                () ->
                        useCase.run(
                                ActorFixtures.administratorWeb(ana.id()),
                                command(opened.sessionToken())),
                ErrorCode.NOT_AUTHENTICATED);
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
    void anInactiveAccountEndsTheAccess() {
        AdministratorSessionResult opened = c.signInAs(ana, PASSWORD);
        c.administrators.save(ana.inactivate(c.clock.now()));

        failWith(
                () ->
                        useCase.run(
                                ActorFixtures.administratorWeb(ana.id()),
                                command(opened.sessionToken())),
                ErrorCode.ACCESS_ENDED);
    }

    @Test
    void readingDoesNotWriteAuthorshipNorChangeTheSession() {
        AdministratorSessionResult opened = c.signInAs(ana, PASSWORD);
        int records = c.recorder.records().size();
        int recordings = c.sessions.recordings();

        useCase.run(ActorFixtures.administratorWeb(ana.id()), command(opened.sessionToken()));

        assertThat(c.recorder.records()).hasSize(records);
        assertThat(c.sessions.recordings()).isEqualTo(recordings);
    }
}
