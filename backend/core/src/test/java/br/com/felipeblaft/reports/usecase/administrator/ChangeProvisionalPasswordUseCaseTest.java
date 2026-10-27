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
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ChangeProvisionalPasswordUseCaseTest {

    private static final String PROVISIONAL = "provisional password";
    private static final String NEW_PASSWORD = "a definitive password";

    private final AdministratorScenario c = new AdministratorScenario();
    private final ChangeProvisionalPasswordUseCase useCase =
            new ChangeProvisionalPasswordUseCase(
                    c.authorization,
                    c.administrators,
                    c.sessions,
                    c.revocation,
                    c.hasher,
                    c.protection,
                    c.authorship,
                    c.clock,
                    AdministratorScenario.SESSION_DURATION);
    private final ValidateAdministratorSessionUseCase validate =
            new ValidateAdministratorSessionUseCase(c.sessions, c.protection, c.clock);
    private final Administrator first = c.provisional("Ana Souza", "ana@school.test", PROVISIONAL);
    private final Actor restricted = Actor.administrator(first.id(), true);

    private AdministratorSessionResult change(Actor actor, String password) {
        return useCase.run(
                actor,
                new ChangeProvisionalPasswordUseCase.Command(
                        password == null ? null : new Secret(password), CORRELATION));
    }

    @Test
    void definesTheNewPasswordAndMakesItDefinitive() {
        change(restricted, NEW_PASSWORD);

        Administrator saved = c.administrators.searchById(first.id()).orElseThrow();
        assertThat(saved.passwordProvisional()).isFalse();
        assertThat(saved.passwordProvisionalExpiresAt()).isNull();
        assertThat(c.hasher.matches(new Secret(NEW_PASSWORD), saved.passwordHash())).isTrue();
        assertThat(saved.updatedAt()).isEqualTo(c.clock.now());
    }

    @Test
    void theProvisionalPasswordStopsWorking() {
        change(restricted, NEW_PASSWORD);

        failWith(() -> c.signInAs(first, PROVISIONAL), ErrorCode.INVALID_CREDENTIALS);
        AdministratorSessionResult session = c.signInAs(first, NEW_PASSWORD);
        assertThat(session.mustChangePassword()).isFalse();
    }

    @Test
    void opensANewFreeSessionAndEndsEveryOtherOne() {
        AdministratorSessionResult current = c.signInAs(first, PROVISIONAL);
        AdministratorSessionResult other = c.signInAs(first, PROVISIONAL);
        c.clock.advance(Duration.ofMinutes(10));

        AdministratorSessionResult result = change(restricted, NEW_PASSWORD);

        assertThat(result.administratorId()).isEqualTo(first.id());
        assertThat(result.name()).isEqualTo("Ana Souza");
        assertThat(result.mustChangePassword()).isFalse();
        assertThat(result.sessionToken()).isNotIn(current.sessionToken(), other.sessionToken());
        assertThat(result.expiresAt()).isEqualTo(c.clock.now().plus(Duration.ofHours(2)));
        failWith(() -> validate.run(current.sessionToken()), ErrorCode.SESSION_EXPIRED);
        failWith(() -> validate.run(other.sessionToken()), ErrorCode.SESSION_EXPIRED);
        ValidateAdministratorSessionUseCase.Result valid = validate.run(result.sessionToken());
        assertThat(valid.actor().passwordChangeRequired()).isFalse();
        assertThat(c.sessions.ofAdministrator(first.id()))
                .filteredOn(session -> session.revoked())
                .hasSize(2)
                .allSatisfy(
                        session ->
                                assertThat(session.revocationReason())
                                        .isEqualTo(SessionRevocationReason.PASSWORD_CHANGED));
    }

    @Test
    void theNewSessionHasANewIdentifier() {
        AdministratorSessionResult current = c.signInAs(first, PROVISIONAL);
        var currentId = c.sessions.all().getFirst().id();

        AdministratorSessionResult result = change(restricted, NEW_PASSWORD);

        assertThat(result.sessionToken()).isNotEqualTo(current.sessionToken());
        assertThat(c.sessions.all()).hasSize(2);
        assertThat(c.sessions.all().get(1).id()).isNotEqualTo(currentId);
    }

    @Test
    void doesNotTouchTheSessionsOfOtherAdministrators() {
        Administrator bia = c.activated("Bia Lima", "bia@school.test");
        AdministratorSessionResult others = c.signInAs(bia, PASSWORD);

        change(restricted, NEW_PASSWORD);

        assertThat(validate.run(others.sessionToken()).actor().id()).isEqualTo(bia.id());
    }

    @Test
    void theNewPasswordMustDifferFromTheProvisionalOne() {
        failWith(() -> change(restricted, PROVISIONAL), ErrorCode.PASSWORD_SAME_AS_PROVISIONAL);

        Administrator saved = c.administrators.searchById(first.id()).orElseThrow();
        assertThat(saved.passwordProvisional()).isTrue();
        assertThat(c.sessions.all()).isEmpty();
    }

    @Test
    void theProvisionalComparisonUsesTheNormalizedPassword() {
        Administrator accented =
                c.provisional("Bia Lima", "bia@school.test", "senha-café-provisoria");

        failWith(
                () -> change(Actor.administrator(accented.id(), true), "senha-café-provisoria"),
                ErrorCode.PASSWORD_SAME_AS_PROVISIONAL);
    }

    @Test
    void theNewPasswordFollowsThePolicy() {
        failWith(() -> change(restricted, "too short"), ErrorCode.PASSWORD_LENGTH);
        failWith(() -> change(restricted, "a".repeat(129)), ErrorCode.PASSWORD_LENGTH);
        failWith(() -> change(restricted, null), ErrorCode.PASSWORD_LENGTH);
        failWith(
                () -> change(restricted, "ANA@school.test!!".substring(0, 15)),
                ErrorCode.PASSWORD_EQUALS_IDENTITY);

        assertThat(c.administrators.searchById(first.id()).orElseThrow().passwordProvisional())
                .isTrue();
    }

    @Test
    void anAdministratorWithADefinitivePasswordDoesNotNeedTheChange() {
        Administrator bia = c.activated("Bia Lima", "bia@school.test");

        failWith(
                () -> change(ActorFixtures.administratorWeb(bia.id()), NEW_PASSWORD),
                ErrorCode.PASSWORD_CHANGE_NOT_REQUIRED);
        assertThat(c.administrators.searchById(bia.id()).orElseThrow()).isEqualTo(bia);
    }

    @Test
    void aStaleRestrictedActorAfterTheChangeIsRefused() {
        change(restricted, NEW_PASSWORD);

        failWith(
                () -> change(restricted, "yet another password"),
                ErrorCode.PASSWORD_CHANGE_NOT_REQUIRED);
    }

    @Test
    void anInactiveOrUnknownAccountEndsTheAccess() {
        c.administrators.save(first.inactivate(c.clock.now()));

        failWith(() -> change(restricted, NEW_PASSWORD), ErrorCode.ACCESS_ENDED);
        failWith(
                () -> change(Actor.administrator(java.util.UUID.randomUUID(), true), NEW_PASSWORD),
                ErrorCode.ACCESS_ENDED);
    }

    @Test
    void aTeacherCannotChangeAnAdministratorPassword() {
        failWith(
                () -> change(ActorFixtures.teacherWeb(), NEW_PASSWORD),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> change(ActorFixtures.teacherTelegram(), NEW_PASSWORD),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void writesTheChangeAuthorshipInTheSameTransactionWithoutAnyPassword() {
        change(restricted, NEW_PASSWORD);

        AuthorshipRecord record = c.recorder.ofOperation("ADMIN_CHANGE_PASSWORD").getFirst();
        assertThat(c.recorder.inTransaction()).contains(record);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.authorId()).isEqualTo(first.id());
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.ADMINISTRATOR);
        assertThat(record.targetId()).isEqualTo(first.id());
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(c.recorder.records().toString()).doesNotContain(PROVISIONAL, NEW_PASSWORD);
    }

    @Test
    void theCommandDoesNotRevealThePassword() {
        assertThat(
                        new ChangeProvisionalPasswordUseCase.Command(
                                        new Secret(NEW_PASSWORD), CORRELATION)
                                .toString())
                .doesNotContain(NEW_PASSWORD);
    }
}
