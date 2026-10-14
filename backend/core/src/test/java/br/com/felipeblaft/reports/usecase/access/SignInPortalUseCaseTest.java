package br.com.felipeblaft.reports.usecase.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.doubles.AccessScenario;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class SignInPortalUseCaseTest {

    private final AccessScenario c = new AccessScenario();
    private final SignInPortalUseCase useCase =
            new SignInPortalUseCase(
                    c.authentication,
                    c.renewal,
                    c.sessions,
                    c.links,
                    c.protection,
                    c.clock,
                    c.parameters);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");

    private static void failWith(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }

    @Test
    void validCodeCreatesSessionWithTokenHash() {
        SignInPortalUseCase.Result result = useCase.run(" k7qm2xpa ", "10.0.0.1");

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
    void reportsWhetherTelegramIsLinked() {
        c.links.save(TelegramLink.create(clara.id(), 1L, 1L, c.clock.now()));
        assertThat(useCase.run("K7QM2XPA", "ip").telegramLinked()).isTrue();
    }

    @Test
    void invalidCodeOrOutsideFormat() {
        failWith(() -> useCase.run("ZZZZZZZZ", "ip"), ErrorCode.INVALID_CODE);
        failWith(() -> useCase.run("short", "ip"), ErrorCode.INVALID_CODE);
        failWith(() -> useCase.run(null, "ip"), ErrorCode.INVALID_CODE);
        assertThat(c.attemptsRepository.all().getFirst().failedAttempts()).isEqualTo(3);
        assertThat(c.sessions.all()).isEmpty();
    }

    @Test
    void inactiveTeacherCannotSignIn() {
        clara.updateRegistry(clara.name(), false, c.clock.now());
        failWith(() -> useCase.run("K7QM2XPA", "ip"), ErrorCode.INVALID_CODE);
    }

    @Test
    void codeExpiredAfterSevenDaysWithoutUse() {
        c.clock.advance(Duration.ofDays(7));
        failWith(() -> useCase.run("K7QM2XPA", "ip"), ErrorCode.EXPIRED_CODE);
        assertThat(c.attemptsRepository.all()).isEmpty();
    }

    @Test
    void blocksOnSixthAttemptEvenWithRightCodeAndResetsOnSuccess() {
        for (int i = 0; i < 5; i++) {
            failWith(() -> useCase.run("ZZZZZZZZ", "ip"), ErrorCode.INVALID_CODE);
        }
        assertThatThrownBy(() -> useCase.run("K7QM2XPA", "ip"))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e -> {
                            DomainException error = (DomainException) e;
                            assertThat(error.code()).isEqualTo(ErrorCode.TEMPORARILY_BLOCKED);
                            assertThat(error.details()).containsEntry("remainingSeconds", 900L);
                        });
        // outra origem não é afetada
        assertThat(useCase.run("K7QM2XPA", "other-ip").teacher().id()).isEqualTo(clara.id());

        c.clock.advance(Duration.ofMinutes(15));
        assertThat(useCase.run("K7QM2XPA", "ip").teacher().id()).isEqualTo(clara.id());
        assertThat(c.attemptsRepository.all())
                .allSatisfy(t -> assertThat(t.failedAttempts()).isZero());
    }

    @Test
    void signInRenewsCodeValidity() {
        c.clock.advance(Duration.ofDays(6));
        useCase.run("K7QM2XPA", "ip");
        assertThat(c.teachers.searchById(clara.id()).orElseThrow().accessCodeExpiresAt())
                .isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
    }
}
