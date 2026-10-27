package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessAttemptScopeTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration FIFTEEN = Duration.ofMinutes(15);
    private static final AttemptPolicy WINDOWED =
            AttemptPolicy.windowed(3, FIFTEEN, Duration.ofMinutes(10));

    @Test
    void createCarriesTheScopeAndDefaultsToTeacherCode() {
        assertThat(AccessAttempt.create(Channel.WEB, "h", T0).scope())
                .isEqualTo(AttemptScope.TEACHER_CODE);
        AccessAttempt admin =
                AccessAttempt.create(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, "h", T0);
        assertThat(admin.scope()).isEqualTo(AttemptScope.ADMIN_SIGN_IN);
        assertThat(admin.windowStartedAt()).isNull();
    }

    @Test
    void failureDuringAnActiveLockNeitherCountsNorExtends() {
        AccessAttempt attempt = AccessAttempt.create(Channel.WEB, "h", T0);
        for (int i = 0; i < 5; i++) {
            attempt.registerFailure(T0, 5, FIFTEEN);
        }
        Instant blockedUntil = attempt.blockedUntil();

        attempt.registerFailure(T0.plus(Duration.ofMinutes(5)), 5, FIFTEEN);

        assertThat(attempt.failedAttempts()).isEqualTo(5);
        assertThat(attempt.blockedUntil()).isEqualTo(blockedUntil);
        assertThat(attempt.updatedAt()).isEqualTo(T0);
    }

    @Test
    void successKeepsAnActiveLockAndResetsOtherwise() {
        AccessAttempt attempt = AccessAttempt.create(Channel.WEB, "h", T0);
        attempt.registerFailure(T0, 2, FIFTEEN);
        assertThat(attempt.confirmSuccess(T0.plusSeconds(1))).isTrue();
        assertThat(attempt.failedAttempts()).isZero();

        attempt.registerFailure(T0, 2, FIFTEEN);
        attempt.registerFailure(T0, 2, FIFTEEN);
        assertThat(attempt.confirmSuccess(T0.plusSeconds(1))).isFalse();
        assertThat(attempt.failedAttempts()).isEqualTo(2);
        assertThat(attempt.confirmSuccess(T0.plus(FIFTEEN))).isTrue();
        assertThat(attempt.blockedUntil()).isNull();
    }

    @Test
    void windowStartsAtTheFirstFailureAndRestartsWhenItHasPassed() {
        AccessAttempt attempt =
                AccessAttempt.create(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, "h", T0);
        attempt.registerFailure(T0, WINDOWED);
        attempt.registerFailure(T0.plusSeconds(30), WINDOWED);
        assertThat(attempt.windowStartedAt()).isEqualTo(T0);
        assertThat(attempt.failedAttempts()).isEqualTo(2);

        Instant late = T0.plus(Duration.ofMinutes(10));
        attempt.registerFailure(late, WINDOWED);

        assertThat(attempt.failedAttempts()).isEqualTo(1);
        assertThat(attempt.windowStartedAt()).isEqualTo(late);
    }

    @Test
    void failureJustInsideTheWindowStillCounts() {
        AccessAttempt attempt =
                AccessAttempt.create(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, "h", T0);
        attempt.registerFailure(T0, WINDOWED);
        attempt.registerFailure(T0.plus(Duration.ofMinutes(10)).minusSeconds(1), WINDOWED);

        assertThat(attempt.failedAttempts()).isEqualTo(2);
    }

    @Test
    void lockEndResetsTheWindowToo() {
        AccessAttempt attempt =
                AccessAttempt.create(AttemptScope.TEACHER_CPF_PROBE, Channel.WEB, "h", T0);
        for (int i = 0; i < 3; i++) {
            attempt.registerFailure(T0.plusSeconds(i), WINDOWED);
        }
        Instant after = T0.plus(Duration.ofMinutes(20));

        attempt.registerFailure(after, WINDOWED);

        assertThat(attempt.failedAttempts()).isEqualTo(1);
        assertThat(attempt.windowStartedAt()).isEqualTo(after);
        assertThat(attempt.blockedUntil()).isNull();
    }

    @Test
    void consecutivePolicyNeverStartsAWindow() {
        AccessAttempt attempt = AccessAttempt.create(Channel.WEB, "h", T0);
        attempt.registerFailure(T0, 5, FIFTEEN);

        assertThat(attempt.windowStartedAt()).isNull();
    }
}
