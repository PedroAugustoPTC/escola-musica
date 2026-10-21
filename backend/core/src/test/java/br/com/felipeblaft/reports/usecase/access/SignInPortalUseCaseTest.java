package br.com.felipeblaft.reports.usecase.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.usecase.teacher.TeacherScenario;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class SignInPortalUseCaseTest {

    private static final String CORRELATION = "55555555-5555-4555-8555-555555555555";

    private final TeacherScenario c = new TeacherScenario();
    private final SignInPortalUseCase useCase =
            new SignInPortalUseCase(
                    c.authentication,
                    c.renewal,
                    c.sessions,
                    c.links,
                    c.protection,
                    c.authorship,
                    c.clock,
                    c.parameters);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");

    private static void failWith(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }

    private SignInPortalUseCase.Result signIn(String code, String origin) {
        return useCase.run(code, origin, CORRELATION);
    }

    @Test
    void validCodeCreatesSessionWithTokenHash() {
        SignInPortalUseCase.Result result = signIn(" k7qm2xpa ", "10.0.0.1");

        assertThat(result.teacher().id()).isEqualTo(clara.id());
        assertThat(result.token()).isEqualTo("token-1");
        assertThat(result.session().tokenHash()).isEqualTo("hash:token-1");
        assertThat(result.session().expiresAt())
                .isEqualTo(c.clock.now().plus(Duration.ofHours(12)));
        assertThat(result.telegramLinked()).isFalse();
        assertThat(result.toString()).doesNotContain("token-1");
        assertThat(c.sessions.all()).hasSize(1);
    }

    @Test
    void overloadWithoutCorrelationStillWorksAndRecords() {
        assertThat(useCase.run("K7QM2XPA", "ip").teacher().id()).isEqualTo(clara.id());
        assertThat(c.recorder.ofOperation("TEACHER_SIGN_IN")).hasSize(1);
    }

    @Test
    void reportsWhetherTelegramIsLinked() {
        c.links.save(TelegramLink.create(clara.id(), 1L, 1L, c.clock.now()));
        assertThat(signIn("K7QM2XPA", "ip").telegramLinked()).isTrue();
    }

    @Test
    void invalidCodeOrOutsideFormat() {
        failWith(() -> signIn("ZZZZZZZZ", "ip"), ErrorCode.INVALID_CODE);
        failWith(() -> signIn("short", "ip"), ErrorCode.INVALID_CODE);
        failWith(() -> signIn(null, "ip"), ErrorCode.INVALID_CODE);
        assertThat(c.attemptsRepository.all().getFirst().failedAttempts()).isEqualTo(3);
        assertThat(c.sessions.all()).isEmpty();
    }

    @Test
    void inactiveTeacherCannotSignIn() {
        clara.inactivate(c.clock.now());
        failWith(() -> signIn("K7QM2XPA", "ip"), ErrorCode.INVALID_CODE);
    }

    @Test
    void codeExpiredAfterSevenDaysWithoutUseCountsAsAnAttempt() {
        c.clock.advance(Duration.ofDays(7));
        failWith(() -> signIn("K7QM2XPA", "ip"), ErrorCode.INVALID_CODE);
        assertThat(c.attemptsRepository.all().getFirst().failedAttempts()).isEqualTo(1);
    }

    @Test
    void blocksOnSixthAttemptEvenWithRightCodeAndResetsOnSuccess() {
        for (int i = 0; i < 5; i++) {
            failWith(() -> signIn("ZZZZZZZZ", "ip"), ErrorCode.INVALID_CODE);
        }
        assertThatThrownBy(() -> signIn("K7QM2XPA", "ip"))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e -> {
                            DomainException error = (DomainException) e;
                            assertThat(error.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED);
                            assertThat(error.details()).containsEntry("remainingSeconds", 900L);
                        });
        // outra origem não é afetada
        assertThat(signIn("K7QM2XPA", "other-ip").teacher().id()).isEqualTo(clara.id());

        c.clock.advance(Duration.ofMinutes(15));
        assertThat(signIn("K7QM2XPA", "ip").teacher().id()).isEqualTo(clara.id());
        assertThat(c.attemptsRepository.all())
                .allSatisfy(t -> assertThat(t.failedAttempts()).isZero());
    }

    @Test
    void signInRenewsCodeValidity() {
        c.clock.advance(Duration.ofDays(6));
        signIn("K7QM2XPA", "ip");
        assertThat(c.teachers.searchById(clara.id()).orElseThrow().accessCodeExpiresAt())
                .isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
    }

    // ---- autoria (FR-040)

    @Test
    void successRecordsTeacherSignInInTheSameTransactionWithOriginHashOnly() {
        signIn("K7QM2XPA", "10.0.0.1");

        assertThat(c.recorder.records()).hasSize(1);
        AuthorshipRecord record = c.recorder.inTransaction().getFirst();
        assertThat(record.operation()).isEqualTo("TEACHER_SIGN_IN");
        assertThat(record.authorRole()).isEqualTo(Role.TEACHER);
        assertThat(record.authorId()).isEqualTo(clara.id());
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.originHash()).isEqualTo(c.protection.originHash(Channel.WEB, "10.0.0.1"));
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(record.toString()).doesNotContain("K7QM2XPA").doesNotContain("10.0.0.1");
    }

    @Test
    void unknownCodeRecordsFailureWithNullAuthorAndNeverTheTypedCode() {
        failWith(() -> signIn("ZZZZZZZZ", "ip"), ErrorCode.INVALID_CODE);

        AuthorshipRecord record = c.recorder.independent().getFirst();
        assertThat(record.operation()).isEqualTo("TEACHER_SIGN_IN");
        assertThat(record.result()).isEqualTo(AuthorshipResult.FAILURE);
        assertThat(record.authorId()).isNull();
        assertThat(record.authorRole()).isNull();
        assertThat(record.errorCode()).isEqualTo("UNKNOWN_CODE");
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(record.originHash()).isEqualTo(c.protection.originHash(Channel.WEB, "ip"));
        assertThat(record.toString()).doesNotContain("ZZZZZZZZ");
        assertThat(c.recorder.inTransaction()).isEmpty();
    }

    @Test
    void malformedCodeRecordsFailureWithoutEchoingIt() {
        failWith(() -> signIn("senha-colada-aqui", "ip"), ErrorCode.INVALID_CODE);

        AuthorshipRecord record = c.recorder.independent().getFirst();
        assertThat(record.errorCode()).isEqualTo("UNKNOWN_CODE");
        assertThat(record.toString()).doesNotContain("senha");
    }

    @Test
    void expiredCodeRecordsFailureOfTheKnownTeacher() {
        c.clock.advance(Duration.ofDays(7));
        failWith(() -> signIn("K7QM2XPA", "ip"), ErrorCode.INVALID_CODE);

        AuthorshipRecord record = c.recorder.independent().getFirst();
        assertThat(record.authorRole()).isEqualTo(Role.TEACHER);
        assertThat(record.authorId()).isEqualTo(clara.id());
        assertThat(record.errorCode()).isEqualTo("CODE_EXPIRED");
        assertThat(record.result()).isEqualTo(AuthorshipResult.FAILURE);
    }

    @Test
    void blockRecordsAttemptBlockedOncePerRefusedAttempt() {
        for (int i = 0; i < 5; i++) {
            failWith(() -> signIn("ZZZZZZZZ", "ip"), ErrorCode.INVALID_CODE);
        }
        failWith(() -> signIn("K7QM2XPA", "ip"), ErrorCode.TEMPORARILY_BLOCKED);

        assertThat(c.recorder.ofOperation("ATTEMPT_BLOCKED")).hasSize(1);
        AuthorshipRecord blocked = c.recorder.ofOperation("ATTEMPT_BLOCKED").getFirst();
        assertThat(blocked.result()).isEqualTo(AuthorshipResult.BLOCKED);
        assertThat(blocked.errorCode()).isEqualTo("TEMPORARILY_BLOCKED");
        assertThat(blocked.authorId()).isNull();
        assertThat(blocked.authorRole()).isNull();
        assertThat(blocked.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(blocked.originHash()).isEqualTo(c.protection.originHash(Channel.WEB, "ip"));
        assertThat(c.recorder.ofOperation("TEACHER_SIGN_IN")).hasSize(5);
        assertThat(c.sessions.all()).isEmpty();
    }

    @Test
    void failureToRecordAuthorshipKeepsTheAccessRefused() {
        c.recorder.failNextRecordWith(new IllegalStateException("falha ao gravar"));

        assertThatThrownBy(() -> signIn("K7QM2XPA", "ip"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(c.sessions.all()).isEmpty();
    }
}
