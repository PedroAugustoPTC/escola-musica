package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.PASSWORD;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ValidateAdministratorSessionUseCaseTest {

    private final AdministratorScenario c = new AdministratorScenario();
    private final ValidateAdministratorSessionUseCase useCase =
            new ValidateAdministratorSessionUseCase(c.sessions, c.protection, c.clock);
    private final Administrator ana = c.activated("Ana Souza", "ana@school.test");

    private Secret signIn(Administrator administrator, String password) {
        return c.signInAs(administrator, password).sessionToken();
    }

    @Test
    void aValidSessionYieldsTheAdministratorActorWithTheFixedExpiry() {
        AdministratorSessionResult opened = c.signInAs(ana, PASSWORD);

        ValidateAdministratorSessionUseCase.Result result = useCase.run(opened.sessionToken());

        assertThat(result.actor().role()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(result.actor().id()).isEqualTo(ana.id());
        assertThat(result.actor().origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(result.actor().passwordChangeRequired()).isFalse();
        assertThat(result.expiresAt()).isEqualTo(opened.expiresAt());
    }

    @Test
    void validatingNeverRenewsTheSession() {
        AdministratorSessionResult opened = c.signInAs(ana, PASSWORD);
        int recordings = c.sessions.recordings();

        c.clock.advance(Duration.ofMinutes(90));
        ValidateAdministratorSessionUseCase.Result result = useCase.run(opened.sessionToken());

        assertThat(result.expiresAt()).isEqualTo(opened.expiresAt());
        assertThat(c.sessions.recordings()).isEqualTo(recordings);
        assertThat(c.sessions.all().getFirst().expiresAt()).isEqualTo(opened.expiresAt());
    }

    @Test
    void theSessionIsValidUntilTheLastMomentBeforeTwoHours() {
        Secret token = signIn(ana, PASSWORD);

        c.clock.advance(Duration.ofHours(2).minusMillis(1));

        assertThat(useCase.run(token).actor().id()).isEqualTo(ana.id());
    }

    @Test
    void exactlyTwoHoursAfterTheSignInTheSessionExpires() {
        Secret token = signIn(ana, PASSWORD);

        c.clock.advance(Duration.ofHours(2));

        failWith(() -> useCase.run(token), ErrorCode.SESSION_EXPIRED);
    }

    @Test
    void anUnknownOrMissingTokenIsNotAuthenticated() {
        failWith(() -> useCase.run(new Secret("unknown")), ErrorCode.NOT_AUTHENTICATED);
        failWith(() -> useCase.run(new Secret("  ")), ErrorCode.NOT_AUTHENTICATED);
        failWith(() -> useCase.run(null), ErrorCode.NOT_AUTHENTICATED);
    }

    @Test
    void anInactivatedAccountEndsTheAccessOnTheNextRequest() {
        Secret token = signIn(ana, PASSWORD);
        assertThat(useCase.run(token)).isNotNull();

        c.revocation.revokeAdministrator(ana.id(), SessionRevocationReason.ACCOUNT_INACTIVATED);
        c.administrators.save(ana.inactivate(c.clock.now()));

        failWith(() -> useCase.run(token), ErrorCode.ACCESS_ENDED);
    }

    @Test
    void anInactiveAccountEndsTheAccessEvenIfTheSessionWasNotMarked() {
        Secret token = signIn(ana, PASSWORD);
        c.administrators.save(ana.inactivate(c.clock.now()));

        failWith(() -> useCase.run(token), ErrorCode.ACCESS_ENDED);
    }

    @Test
    void reactivatingTheAccountDoesNotResurrectTheOldSession() {
        Secret token = signIn(ana, PASSWORD);
        c.revocation.revokeAdministrator(ana.id(), SessionRevocationReason.ACCOUNT_INACTIVATED);
        Administrator inactive = ana.inactivate(c.clock.now());
        c.administrators.save(
                inactive.reactivate("new-hash", c.clock.now().plusSeconds(60), c.clock.now()));

        failWith(() -> useCase.run(token), ErrorCode.ACCESS_ENDED);
    }

    @Test
    void aSessionRevokedByAPasswordChangeIsExpired() {
        Secret token = signIn(ana, PASSWORD);

        c.revocation.revokeAdministrator(ana.id(), SessionRevocationReason.PASSWORD_CHANGED);

        failWith(() -> useCase.run(token), ErrorCode.SESSION_EXPIRED);
    }

    @Test
    void aRevokedSessionDoesNotAffectTheOtherSessionsOfOtherAdministrators() {
        Administrator bia = c.activated("Bia Lima", "bia@school.test");
        Secret anaToken = signIn(ana, PASSWORD);
        Secret biaToken = signIn(bia, PASSWORD);

        c.revocation.revokeAdministrator(ana.id(), SessionRevocationReason.PASSWORD_CHANGED);

        failWith(() -> useCase.run(anaToken), ErrorCode.SESSION_EXPIRED);
        assertThat(useCase.run(biaToken).actor().id()).isEqualTo(bia.id());
    }

    @Test
    void theActorOfAProvisionalPasswordIsRestricted() {
        Administrator first =
                c.provisional("Cris Prado", "cris@school.test", "provisional password");
        Secret token = signIn(first, "provisional password");

        assertThat(useCase.run(token).actor().passwordChangeRequired()).isTrue();
    }

    @Test
    void validatingDoesNotWriteAuthorship() {
        Secret token = signIn(ana, PASSWORD);
        int before = c.recorder.records().size();

        useCase.run(token);

        assertThat(c.recorder.records()).hasSize(before);
    }
}
