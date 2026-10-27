package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.ORIGIN;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.refusalOf;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ActivateAdministratorUseCaseTest {

    private static final String CODE = "KQMXPTRWHNCV";
    private static final String NEW_PASSWORD = "a brand new password";

    private final AdministratorScenario c = new AdministratorScenario();
    private final ActivateAdministratorUseCase useCase =
            new ActivateAdministratorUseCase(
                    c.administrators, c.protection, c.hasher, c.attempts, c.authorship, c.clock);
    private final Administrator bia = c.pending("Bia Lima", "bia@school.test", CODE);

    private ActivateAdministratorUseCase.Command command(
            String email, String code, String password) {
        return command(email, code, password, ORIGIN);
    }

    private ActivateAdministratorUseCase.Command command(
            String email, String code, String password, String origin) {
        return new ActivateAdministratorUseCase.Command(
                email,
                code == null ? null : new Secret(code),
                password == null ? null : new Secret(password),
                origin,
                CORRELATION);
    }

    private void invalidActivation(String email, String code) {
        failWith(
                () -> useCase.run(command(email, code, NEW_PASSWORD)),
                ErrorCode.INVALID_ACTIVATION);
    }

    @Test
    void activatesWithTheEmailTheCodeAndTheNewPassword() {
        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));

        Administrator saved = c.administrators.searchById(bia.id()).orElseThrow();
        assertThat(saved.activated()).isTrue();
        assertThat(c.hasher.matches(new Secret(NEW_PASSWORD), saved.passwordHash())).isTrue();
        assertThat(saved.activatedAt()).isEqualTo(c.clock.now());
        assertThat(saved.activationCodeHash()).isNull();
        assertThat(saved.activationCodeExpiresAt()).isNull();
        assertThat(saved.passwordProvisional()).isFalse();
        assertThat(c.sessions.all()).isEmpty();
    }

    @Test
    void theCodeAndTheEmailAreNormalized() {
        useCase.run(command("  BIA@School.test ", "kqmx-ptrw-hncv", NEW_PASSWORD));

        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isTrue();
    }

    @Test
    void afterTheActivationTheAdministratorCanSignIn() {
        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));

        AdministratorSessionResult session =
                c.signInAs(c.administrators.searchById(bia.id()).orElseThrow(), NEW_PASSWORD);

        assertThat(session.administratorId()).isEqualTo(bia.id());
        assertThat(session.mustChangePassword()).isFalse();
    }

    @Test
    void beforeTheActivationTheAdministratorCannotSignIn() {
        failWith(() -> c.signInAs(bia, NEW_PASSWORD), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void thePasswordIsStoredInNfc() {
        useCase.run(command("bia@school.test", CODE, "senha-café-bem-longa"));

        Administrator saved = c.administrators.searchById(bia.id()).orElseThrow();
        assertThat(c.hasher.matches(new Secret("senha-café-bem-longa"), saved.passwordHash()))
                .isTrue();
    }

    @Test
    void missingValuesAreRefusedLikeAWrongCode() {
        invalidActivation(null, CODE);
        invalidActivation("bia@school.test", null);
        failWith(
                () -> useCase.run(command("bia@school.test", CODE, null)),
                ErrorCode.PASSWORD_LENGTH);
        failWith(
                () -> useCase.run(command("bia@school.test", "ZZZZZZZZZZZZ", NEW_PASSWORD, null)),
                ErrorCode.INVALID_ACTIVATION);

        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isFalse();
    }

    @Test
    void theCodeIsSingleUse() {
        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));

        invalidActivation("bia@school.test", CODE);
        Administrator saved = c.administrators.searchById(bia.id()).orElseThrow();
        assertThat(c.hasher.matches(new Secret(NEW_PASSWORD), saved.passwordHash())).isTrue();
    }

    @Test
    void wrongExpiredInactiveUnknownAndMalformedCodesGetTheSameRefusal() {
        Administrator caio = c.pending("Caio Dias", "caio@school.test", "ABCDEFGHJKLM");
        c.administrators.save(caio.inactivate(c.clock.now()));

        DomainException wrong =
                refusalOf(
                        () ->
                                useCase.run(
                                        command("bia@school.test", "ZZZZZZZZZZZZ", NEW_PASSWORD)));
        DomainException inactive =
                refusalOf(
                        () ->
                                useCase.run(
                                        command(
                                                "caio@school.test",
                                                "ABCDEFGHJKLM",
                                                NEW_PASSWORD,
                                                "10.0.0.2")));
        DomainException unknown =
                refusalOf(
                        () ->
                                useCase.run(
                                        command(
                                                "nobody@school.test",
                                                CODE,
                                                NEW_PASSWORD,
                                                "10.0.0.3")));
        DomainException malformed =
                refusalOf(
                        () ->
                                useCase.run(
                                        command(
                                                "bia@school.test",
                                                "short",
                                                NEW_PASSWORD,
                                                "10.0.0.4")));
        c.clock.advance(Duration.ofDays(7));
        DomainException expired =
                refusalOf(
                        () ->
                                useCase.run(
                                        command(
                                                "bia@school.test",
                                                CODE,
                                                NEW_PASSWORD,
                                                "10.0.0.5")));

        for (DomainException refusal :
                new DomainException[] {wrong, inactive, unknown, malformed, expired}) {
            assertThat(refusal.code()).isEqualTo(ErrorCode.INVALID_ACTIVATION);
            assertThat(refusal.details()).isEmpty();
            assertThat(refusal.getMessage()).isEqualTo(wrong.getMessage());
        }
        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isFalse();
    }

    @Test
    void theCodeOfAnotherAdministratorDoesNotActivateThisOne() {
        c.pending("Caio Dias", "caio@school.test", "ABCDEFGHJKLM");

        invalidActivation("bia@school.test", "ABCDEFGHJKLM");
        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isFalse();
    }

    @Test
    void theCodeExpiresAtTheEndOfSevenDays() {
        c.clock.advance(Duration.ofDays(7).minusSeconds(1));

        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));

        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isTrue();
    }

    @Test
    void fiveWrongCodesBlockTheOriginEvenForTheRightCodeWithTheRemainingTime() {
        for (int i = 0; i < 5; i++) {
            invalidActivation("bia@school.test", "ZZZZZZZZZZZZ");
        }

        DomainException blocked =
                refusalOf(() -> useCase.run(command("bia@school.test", CODE, NEW_PASSWORD)));

        assertThat(blocked.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED);
        assertThat(blocked.details()).containsEntry("remainingSeconds", 900L);
        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isFalse();
    }

    @Test
    void theBlockEndsAfterFifteenMinutes() {
        for (int i = 0; i < 5; i++) {
            invalidActivation("bia@school.test", "ZZZZZZZZZZZZ");
        }
        c.clock.advance(Duration.ofMinutes(15));

        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));

        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isTrue();
    }

    @Test
    void theCountIsSeparateFromTheSignIn() {
        Administrator ana = c.activated("Ana Souza", "ana@school.test");
        for (int i = 0; i < 5; i++) {
            failWith(() -> c.signInAs(ana, "wrong password!"), ErrorCode.INVALID_CREDENTIALS);
        }

        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));

        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isTrue();
    }

    @Test
    void theActivationFailuresDoNotBlockTheSignIn() {
        Administrator ana = c.activated("Ana Souza", "ana@school.test");
        for (int i = 0; i < 5; i++) {
            invalidActivation("bia@school.test", "ZZZZZZZZZZZZ");
        }

        assertThat(c.signInAs(ana, AdministratorScenario.PASSWORD).administratorId())
                .isEqualTo(ana.id());
    }

    @Test
    void aSuccessResetsTheCount() {
        Administrator caio = c.pending("Caio Dias", "caio@school.test", "ABCDEFGHJKLM");
        for (int i = 0; i < 4; i++) {
            invalidActivation("bia@school.test", "ZZZZZZZZZZZZ");
        }
        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));

        for (int i = 0; i < 4; i++) {
            invalidActivation("caio@school.test", "ZZZZZZZZZZZZ");
        }
        useCase.run(command(caio.email(), "ABCDEFGHJKLM", NEW_PASSWORD));

        assertThat(c.administrators.searchById(caio.id()).orElseThrow().activated()).isTrue();
    }

    @Test
    void aPasswordOutsideThePolicyIsRefusedWithoutConsumingTheCodeNorCountingAFailure() {
        failWith(
                () -> useCase.run(command("bia@school.test", CODE, "too short")),
                ErrorCode.PASSWORD_LENGTH);
        failWith(
                () -> useCase.run(command("bia@school.test", CODE, "BIA@school.test")),
                ErrorCode.PASSWORD_EQUALS_IDENTITY);
        failWith(
                () -> useCase.run(command("bia@school.test", CODE, "bia lima")),
                ErrorCode.PASSWORD_LENGTH);

        assertThat(c.attemptsRepository.all()).isEmpty();
        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));
        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isTrue();
    }

    @Test
    void thePasswordCannotBeTheNameOfTheAdministrator() {
        Administrator longName =
                c.pending("Maria Aparecida Souza", "maria@school.test", "ABCDEFGHJKLM");

        failWith(
                () ->
                        useCase.run(
                                command(longName.email(), "ABCDEFGHJKLM", "maria aparecida souza")),
                ErrorCode.PASSWORD_EQUALS_IDENTITY);
    }

    @Test
    void theAdministratorWhoCreatedTheAccountMayUseTheCode() {
        Administrator ana = c.activated("Ana Souza", "ana@school.test");
        AdministratorWithCode created =
                c.created(
                        ActorFixtures.administratorWeb(ana.id()), "Caio Dias", "caio@school.test");

        useCase.run(command("caio@school.test", created.activationCode().value(), NEW_PASSWORD));

        assertThat(c.administrators.searchByEmail("caio@school.test").orElseThrow().activated())
                .isTrue();
    }

    @Test
    void aReactivatedAccountNeedsTheNewCode() {
        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));
        Administrator active = c.administrators.searchById(bia.id()).orElseThrow();
        c.administrators.save(
                active.inactivate(c.clock.now())
                        .reactivate(
                                c.protection.hashActivationCode(
                                        br.com.felipeblaft.reports.domain.access.ActivationCode.of(
                                                "ABCDEFGHJKLM")),
                                c.clock.now().plus(Duration.ofDays(7)),
                                c.clock.now()));

        invalidActivation("bia@school.test", CODE);
        useCase.run(command("bia@school.test", "ABCDEFGHJKLM", "another long password"));

        assertThat(c.administrators.searchById(bia.id()).orElseThrow().activated()).isTrue();
    }

    @Test
    void successWritesTheActivationAuthorshipInTheSameTransaction() {
        useCase.run(command("bia@school.test", CODE, NEW_PASSWORD));

        AuthorshipRecord record = c.recorder.ofOperation("ADMIN_ACTIVATE").getFirst();
        assertThat(c.recorder.inTransaction()).contains(record);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.authorId()).isEqualTo(bia.id());
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(record.originHash())
                .isEqualTo(c.protection.originHash(AttemptScope.ADMIN_ACTIVATION, ORIGIN));
    }

    @Test
    void failureIsRecordedIndependentlyWithoutTheEmailNorTheCode() {
        invalidActivation("secret.typed@school.test", CODE);
        invalidActivation("bia@school.test", "ZZZZZZZZZZZZ");

        AuthorshipRecord unknown = c.recorder.independent().get(0);
        AuthorshipRecord known = c.recorder.independent().get(1);
        assertThat(unknown.operation()).isEqualTo("ADMIN_ACTIVATE");
        assertThat(unknown.result()).isEqualTo(AuthorshipResult.FAILURE);
        assertThat(unknown.errorCode()).isEqualTo("UNKNOWN_EMAIL");
        assertThat(unknown.authorId()).isNull();
        assertThat(known.errorCode()).isEqualTo("INVALID_ACTIVATION");
        assertThat(known.authorId()).isEqualTo(bia.id());
        assertThat(c.recorder.records().toString())
                .doesNotContain("secret.typed", CODE, "ZZZZZZZZZZZZ", NEW_PASSWORD);
    }

    @Test
    void anAttemptRefusedByTheBlockIsRecordedAsBlocked() {
        for (int i = 0; i < 5; i++) {
            invalidActivation("bia@school.test", "ZZZZZZZZZZZZ");
        }

        failWith(
                () -> useCase.run(command("bia@school.test", CODE, NEW_PASSWORD)),
                ErrorCode.TEMPORARILY_BLOCKED);

        AuthorshipRecord record = c.recorder.last().orElseThrow();
        assertThat(record.operation()).isEqualTo("ADMIN_ACTIVATE");
        assertThat(record.result()).isEqualTo(AuthorshipResult.BLOCKED);
        assertThat(record.errorCode()).isEqualTo("TEMPORARILY_BLOCKED");
    }

    @Test
    void theCommandDoesNotRevealItsContent() {
        assertThat(command("bia@school.test", CODE, NEW_PASSWORD).toString())
                .doesNotContain("bia@school.test", CODE, NEW_PASSWORD);
    }
}
