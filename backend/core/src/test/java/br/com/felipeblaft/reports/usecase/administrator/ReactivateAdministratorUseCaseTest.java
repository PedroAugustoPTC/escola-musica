package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.ORIGIN;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.PASSWORD;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReactivateAdministratorUseCaseTest {

    private final AdministratorScenario c = new AdministratorScenario();
    private final ReactivateAdministratorUseCase useCase =
            new ReactivateAdministratorUseCase(
                    c.authorization, c.administrators, c.issuance, c.authorship, c.clock);
    private final Administrator ana = c.activated("Ana Souza", "ana@school.test");
    private final Administrator bia = inactive("Bia Lima", "bia@school.test");
    private final Actor actorAna = ActorFixtures.administratorWeb(ana.id());

    private Administrator inactive(String name, String email) {
        Administrator administrator = c.activated(name, email).inactivate(c.clock.now());
        c.administrators.save(administrator);
        return administrator;
    }

    private AdministratorWithCode reactivate(Actor actor, UUID id) {
        return useCase.run(actor, new ReactivateAdministratorUseCase.Command(id, CORRELATION));
    }

    @Test
    void reactivatesAsPendingActivationWithANewCodeShownOnce() {
        AdministratorWithCode result = reactivate(actorAna, bia.id());

        Administrator saved = c.administrators.searchById(bia.id()).orElseThrow();
        assertThat(saved.active()).isTrue();
        assertThat(saved.passwordHash()).isNull();
        assertThat(saved.pendingActivation()).isTrue();
        assertThat(saved.activationCodeHash())
                .isEqualTo("activation:" + result.activationCode().value());
        assertThat(saved.activationCodeExpiresAt())
                .isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        assertThat(result.validUntil()).isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        assertThat(result.administrator().activationState())
                .isEqualTo(AdministratorView.ActivationState.PENDING);
        assertThat(result.administrator().active()).isTrue();
        assertThat(result.administrator().activatedAt()).isNull();
    }

    @Test
    void theReactivatedOneCannotSignInUntilItDefinesTheNewPassword() {
        reactivate(actorAna, bia.id());

        failWith(() -> c.signInAs(bia, PASSWORD), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void theNewCodeActivatesTheAccountAgain() {
        AdministratorWithCode result = reactivate(actorAna, bia.id());
        ActivateAdministratorUseCase activate =
                new ActivateAdministratorUseCase(
                        c.administrators,
                        c.protection,
                        c.hasher,
                        c.attempts,
                        c.authorship,
                        c.clock);

        activate.run(
                new ActivateAdministratorUseCase.Command(
                        "bia@school.test",
                        new Secret(result.activationCode().value()),
                        new Secret("another long password"),
                        ORIGIN,
                        CORRELATION));

        assertThat(c.signInAs(bia, "another long password").administratorId()).isEqualTo(bia.id());
    }

    @Test
    void theOldSessionsOfTheReactivatedOneStayRevoked() {
        Administrator caio = c.activated("Caio Dias", "caio@school.test");
        AdministratorSessionResult old = c.signInAs(caio, PASSWORD);
        new InactivateAdministratorUseCase(
                        c.authorization, c.administrators, c.revocation, c.authorship, c.clock)
                .run(actorAna, new InactivateAdministratorUseCase.Command(caio.id(), CORRELATION));

        reactivate(actorAna, caio.id());

        failWith(
                () ->
                        new ValidateAdministratorSessionUseCase(c.sessions, c.protection, c.clock)
                                .run(old.sessionToken()),
                ErrorCode.ACCESS_ENDED);
    }

    @Test
    void anActiveAdministratorIsNotReactivated() {
        failWith(() -> reactivate(actorAna, ana.id()), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(c.administrators.searchById(ana.id()).orElseThrow()).isEqualTo(ana);
    }

    @Test
    void anUnknownAdministratorIsNotFound() {
        failWith(() -> reactivate(actorAna, UUID.randomUUID()), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void writesTheReactivationAuthorshipWithoutTheCode() {
        AdministratorWithCode result = reactivate(actorAna, bia.id());

        AuthorshipRecord record = c.recorder.ofOperation("ADMINISTRATOR_REACTIVATE").getFirst();
        assertThat(c.recorder.inTransaction()).contains(record);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.authorId()).isEqualTo(ana.id());
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.ADMINISTRATOR);
        assertThat(record.targetId()).isEqualTo(bia.id());
        assertThat(c.recorder.records().toString()).doesNotContain(result.activationCode().value());
    }

    @Test
    void onlyAnAdministratorMayReactivate() {
        failWith(
                () -> reactivate(ActorFixtures.teacherWeb(), bia.id()),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> reactivate(ActorFixtures.teacherTelegram(), bia.id()),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(c.administrators.searchById(bia.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void anAdministratorWithAProvisionalPasswordCannotReactivate() {
        failWith(
                () -> reactivate(ActorFixtures.administratorWithProvisionalPassword(), bia.id()),
                ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }
}
