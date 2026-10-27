package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.ORIGIN;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.PASSWORD;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SignInAdministratorUseCaseTest {

    private final AdministratorScenario c = new AdministratorScenario();
    private final SignInAdministratorUseCase useCase = c.signIn();
    private final Administrator ana = c.activated("Ana Souza", "ana@school.test");

    private SignInAdministratorUseCase.Command command(String email, String password) {
        return command(email, password, ORIGIN);
    }

    private SignInAdministratorUseCase.Command command(
            String email, String password, String origin) {
        return new SignInAdministratorUseCase.Command(
                email, password == null ? null : new Secret(password), origin, CORRELATION);
    }

    private void fail(String email, String password) {
        failWith(() -> useCase.run(command(email, password)), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void validCredentialsOpenAFixedTwoHourSession() {
        AdministratorSessionResult result = useCase.run(command("ana@school.test", PASSWORD));

        assertThat(result.administratorId()).isEqualTo(ana.id());
        assertThat(result.name()).isEqualTo("Ana Souza");
        assertThat(result.sessionToken().value()).isEqualTo("token-1");
        assertThat(result.expiresAt()).isEqualTo(c.clock.now().plus(Duration.ofHours(2)));
        assertThat(result.mustChangePassword()).isFalse();
        AdministratorSession session = c.sessions.all().getFirst();
        assertThat(session.administratorId()).isEqualTo(ana.id());
        assertThat(session.tokenHash()).isEqualTo("hash:token-1");
        assertThat(session.expiresAt()).isEqualTo(result.expiresAt());
        assertThat(result.toString()).doesNotContain("token-1", "Ana");
    }

    @Test
    void aMissingOriginIsTreatedAsAnEmptyOne() {
        AdministratorSessionResult result = useCase.run(command("ana@school.test", PASSWORD, null));

        assertThat(result.administratorId()).isEqualTo(ana.id());
        assertThat(c.recorder.inTransaction().getFirst().originHash())
                .isEqualTo(c.protection.originHash(AttemptScope.ADMIN_SIGN_IN, ""));
    }

    @Test
    void theEmailIsNormalizedLikeInTheRegistration() {
        AdministratorSessionResult result = useCase.run(command("  ANA@School.TEST ", PASSWORD));

        assertThat(result.administratorId()).isEqualTo(ana.id());
    }

    @Test
    void thePasswordIsMatchedAfterNfcNormalization() {
        Administrator jose = c.activated("Jose Prado", "jose@school.test", "senha-café-ok-ok");

        AdministratorSessionResult result = useCase.run(command(jose.email(), "senha-café-ok-ok"));

        assertThat(result.administratorId()).isEqualTo(jose.id());
    }

    @Test
    void everySignInGetsANewSessionIdAndTokenAndSessionsCoexist() {
        AdministratorSessionResult first = useCase.run(command("ana@school.test", PASSWORD));
        AdministratorSessionResult second = useCase.run(command("ana@school.test", PASSWORD));

        assertThat(second.sessionToken()).isNotEqualTo(first.sessionToken());
        assertThat(c.sessions.all()).hasSize(2);
        assertThat(c.sessions.all().get(0).id()).isNotEqualTo(c.sessions.all().get(1).id());
        assertThat(c.sessions.all()).allMatch(s -> !s.revoked());
    }

    @Test
    void unknownEmailWrongPasswordInactiveAndPendingAccountsGetTheSameRefusal() {
        Administrator inactive =
                c.activated("Bia Lima", "bia@school.test").inactivate(c.clock.now());
        c.administrators.save(inactive);
        c.pending("Caio Dias", "caio@school.test", "KQMXPTRWHNCV");

        DomainException unknown = refusal("nobody@school.test", PASSWORD, "10.0.0.2");
        DomainException wrong = refusal("ana@school.test", "wrong password!", "10.0.0.3");
        DomainException inactiveRefusal = refusal("bia@school.test", PASSWORD, "10.0.0.4");
        DomainException pendingRefusal = refusal("caio@school.test", PASSWORD, "10.0.0.5");

        for (DomainException refusal :
                new DomainException[] {unknown, inactiveRefusal, pendingRefusal}) {
            assertThat(refusal.code())
                    .isEqualTo(wrong.code())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            assertThat(refusal.details()).isEqualTo(wrong.details()).isEmpty();
            assertThat(refusal.getMessage()).isEqualTo(wrong.getMessage());
        }
        assertThat(c.sessions.all()).isEmpty();
    }

    private DomainException refusal(String email, String password, String origin) {
        return (DomainException)
                catchThrowable(() -> useCase.run(command(email, password, origin)));
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            return error;
        }
        throw new AssertionError("a entrada deveria ter sido recusada");
    }

    @Test
    void thePasswordVerificationRunsInFullEvenForAnUnknownEmail() {
        fail("nobody@school.test", PASSWORD);

        assertThat(c.hasher.decoyVerifications()).isEqualTo(1);
    }

    @Test
    void anInactiveOrPendingAccountAlsoRunsTheFullVerification() {
        c.administrators.save(ana.inactivate(c.clock.now()));
        c.pending("Caio Dias", "caio@school.test", "KQMXPTRWHNCV");

        fail("ana@school.test", PASSWORD);
        fail("caio@school.test", PASSWORD);

        assertThat(c.hasher.decoyVerifications()).isEqualTo(2);
    }

    @Test
    void aTooLongPasswordIsRefusedLikeAWrongOne() {
        fail("ana@school.test", "a".repeat(5000));

        assertThat(c.hasher.decoyVerifications()).isEqualTo(1);
    }

    @Test
    void missingValuesAreRefusedLikeAWrongPassword() {
        fail(null, PASSWORD);
        fail("   ", PASSWORD);
        fail("ana@school.test", null);

        assertThat(c.attemptsRepository.all().getFirst().failedAttempts()).isEqualTo(3);
    }

    @Test
    void fiveFailuresBlockTheOriginEvenForTheRightPasswordWithTheRemainingTime() {
        for (int i = 0; i < 5; i++) {
            fail("ana@school.test", "wrong password!");
        }
        int decoysBefore = c.hasher.decoyVerifications();

        assertThatThrownBy(() -> useCase.run(command("ana@school.test", PASSWORD)))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error -> {
                            assertThat(error.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED);
                            assertThat(error.details()).containsEntry("remainingSeconds", 900L);
                        });
        assertThat(c.sessions.all()).isEmpty();
        assertThat(c.hasher.decoyVerifications()).isEqualTo(decoysBefore);
    }

    @Test
    void theBlockedMessageDoesNotDependOnTheEmailExisting() {
        for (int i = 0; i < 5; i++) {
            fail("nobody@school.test", "wrong password!");
        }

        for (String email : new String[] {"nobody@school.test", "ana@school.test"}) {
            assertThatThrownBy(() -> useCase.run(command(email, PASSWORD)))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            error -> {
                                assertThat(error.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED);
                                assertThat(error.details()).containsEntry("remainingSeconds", 900L);
                            });
        }
    }

    @Test
    void attemptsDuringTheBlockDoNotCountNorExtendIt() {
        for (int i = 0; i < 5; i++) {
            fail("ana@school.test", "wrong password!");
        }
        c.clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> useCase.run(command("ana@school.test", "wrong password!")))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error ->
                                assertThat(error.details())
                                        .containsEntry("remainingSeconds", 600L));
        c.clock.advance(Duration.ofMinutes(10));

        assertThat(useCase.run(command("ana@school.test", PASSWORD)).administratorId())
                .isEqualTo(ana.id());
    }

    @Test
    void anotherOriginIsNotAffectedByTheBlock() {
        for (int i = 0; i < 5; i++) {
            fail("ana@school.test", "wrong password!");
        }

        assertThat(useCase.run(command("ana@school.test", PASSWORD, "10.0.0.9")).administratorId())
                .isEqualTo(ana.id());
    }

    @Test
    void successResetsTheCount() {
        for (int i = 0; i < 4; i++) {
            fail("ana@school.test", "wrong password!");
        }
        useCase.run(command("ana@school.test", PASSWORD));

        for (int i = 0; i < 4; i++) {
            fail("ana@school.test", "wrong password!");
        }
        assertThat(useCase.run(command("ana@school.test", PASSWORD)).administratorId())
                .isEqualTo(ana.id());
    }

    @Test
    void theCountIsSeparateFromTheTeacherCode() {
        for (int i = 0; i < 5; i++) {
            fail("ana@school.test", "wrong password!");
        }

        String teacherHash =
                c.protection.originHash(
                        br.com.felipeblaft.reports.domain.common.Channel.WEB, ORIGIN);
        assertThat(
                        c.attemptsRepository.search(
                                AttemptScope.TEACHER_CODE,
                                br.com.felipeblaft.reports.domain.common.Channel.WEB,
                                teacherHash))
                .isEmpty();
        assertThat(
                        c.attemptsRepository.search(
                                AttemptScope.ADMIN_SIGN_IN,
                                br.com.felipeblaft.reports.domain.common.Channel.WEB,
                                c.protection.originHash(AttemptScope.ADMIN_SIGN_IN, ORIGIN)))
                .isPresent();
    }

    @Test
    void theProvisionalPasswordForcesThePasswordChange() {
        Administrator first = c.provisional("Bia Lima", "bia@school.test", "provisional password");

        AdministratorSessionResult result =
                useCase.run(command(first.email(), "provisional password"));

        assertThat(result.mustChangePassword()).isTrue();
    }

    @Test
    void anExpiredProvisionalPasswordDoesNotAuthenticateWithTheSameMessage() {
        Administrator first = c.provisional("Bia Lima", "bia@school.test", "provisional password");
        c.clock.advance(Duration.ofDays(7));

        failWith(
                () -> useCase.run(command(first.email(), "provisional password")),
                ErrorCode.INVALID_CREDENTIALS);
        assertThat(c.hasher.decoyVerifications()).isEqualTo(1);
    }

    @Test
    void aSuccessfulSignInRehashesWhenTheParametersWereRaised() {
        c.hasher.raiseParameters();

        useCase.run(command("ana@school.test", PASSWORD));

        Administrator saved = c.administrators.searchById(ana.id()).orElseThrow();
        assertThat(saved.passwordHash()).isNotEqualTo(ana.passwordHash());
        assertThat(c.hasher.needsRehash(saved.passwordHash())).isFalse();
        assertThat(c.hasher.matches(new Secret(PASSWORD), saved.passwordHash())).isTrue();
    }

    @Test
    void withCurrentParametersTheHashIsNotRewritten() {
        int before = c.administrators.recordings();

        useCase.run(command("ana@school.test", PASSWORD));

        assertThat(c.administrators.recordings()).isEqualTo(before);
    }

    @Test
    void successWritesAuthorshipInTheSameTransactionWithTheOriginHash() {
        useCase.run(command("ana@school.test", PASSWORD));

        AuthorshipRecord record = c.recorder.inTransaction().getFirst();
        assertThat(record.operation()).isEqualTo("ADMIN_SIGN_IN");
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.authorId()).isEqualTo(ana.id());
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(record.originHash())
                .isEqualTo(c.protection.originHash(AttemptScope.ADMIN_SIGN_IN, ORIGIN));
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(record.errorCode()).isNull();
    }

    @Test
    void failureOfAKnownAccountIsRecordedIndependentlyWithTheAuthor() {
        fail("ana@school.test", "wrong password!");

        AuthorshipRecord record = c.recorder.independent().getFirst();
        assertThat(record.operation()).isEqualTo("ADMIN_SIGN_IN");
        assertThat(record.result()).isEqualTo(AuthorshipResult.FAILURE);
        assertThat(record.errorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(record.authorId()).isEqualTo(ana.id());
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.originHash()).isNotNull();
        assertThat(c.recorder.inTransaction()).isEmpty();
    }

    @Test
    void failureOfAnUnknownEmailRecordsTheOriginAndNeverTheEmail() {
        fail("secret.typed@school.test", PASSWORD);

        AuthorshipRecord record = c.recorder.independent().getFirst();
        assertThat(record.result()).isEqualTo(AuthorshipResult.FAILURE);
        assertThat(record.errorCode()).isEqualTo("UNKNOWN_EMAIL");
        assertThat(record.authorId()).isNull();
        assertThat(record.authorRole()).isNull();
        assertThat(record.originHash()).isNotNull();
        assertThat(record.toString()).doesNotContain("secret.typed", PASSWORD);
    }

    @Test
    void anAttemptRefusedByTheBlockIsRecordedAsBlocked() {
        for (int i = 0; i < 5; i++) {
            fail("ana@school.test", "wrong password!");
        }
        c.recorder.rollBackTransaction();

        failWith(
                () -> useCase.run(command("ana@school.test", PASSWORD)),
                ErrorCode.TEMPORARILY_BLOCKED);

        AuthorshipRecord record = c.recorder.last().orElseThrow();
        assertThat(record.operation()).isEqualTo("ADMIN_SIGN_IN");
        assertThat(record.result()).isEqualTo(AuthorshipResult.BLOCKED);
        assertThat(record.errorCode()).isEqualTo("TEMPORARILY_BLOCKED");
        assertThat(record.authorId()).isNull();
        assertThat(record.originHash()).isNotNull();
        assertThat(c.recorder.independent()).contains(record);
    }

    @Test
    void neverRecordsThePasswordAnywhere() {
        fail("ana@school.test", "wrong password!");
        useCase.run(command("ana@school.test", PASSWORD));

        assertThat(c.recorder.records().toString()).doesNotContain(PASSWORD, "wrong password!");
    }

    @Test
    void theCommandDoesNotRevealItsContent() {
        assertThat(command("ana@school.test", PASSWORD).toString())
                .doesNotContain("ana@school.test", PASSWORD, ORIGIN);
    }
}
