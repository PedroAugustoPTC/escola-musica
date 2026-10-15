package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessAttemptTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration FIFTEEN = Duration.ofMinutes(15);

    @Test
    void blocksOnFifthConsecutiveFailure() {
        AccessAttempt attempt = AccessAttempt.create(Channel.WEB, "origin", T0);
        for (int i = 1; i <= 4; i++) {
            attempt.registerFailure(T0.plusSeconds(i), 5, FIFTEEN);
            assertThat(attempt.remainingLock(T0.plusSeconds(i))).isEmpty();
        }
        attempt.registerFailure(T0.plusSeconds(5), 5, FIFTEEN);
        assertThat(attempt.failedAttempts()).isEqualTo(5);
        assertThat(attempt.blockedUntil()).isEqualTo(T0.plusSeconds(5).plus(FIFTEEN));
        assertThat(attempt.remainingLock(T0.plusSeconds(65))).contains(Duration.ofMinutes(14));
        assertThat(attempt.lockEnded(T0.plusSeconds(65))).isFalse();
        assertThat(attempt.channel()).isEqualTo(Channel.WEB);
        assertThat(attempt.originHash()).isEqualTo("origin");
    }

    @Test
    void lockEndResetsBeforeCountingNewFailure() {
        AccessAttempt attempt = AccessAttempt.create(Channel.TELEGRAM, "origin", T0);
        for (int i = 0; i < 5; i++) {
            attempt.registerFailure(T0, 5, FIFTEEN);
        }
        Instant after = T0.plus(FIFTEEN);
        assertThat(attempt.lockEnded(after)).isTrue();
        assertThat(attempt.remainingLock(after)).isEmpty();

        attempt.registerFailure(after, 5, FIFTEEN);

        assertThat(attempt.failedAttempts()).isEqualTo(1);
        assertThat(attempt.blockedUntil()).isNull();
        assertThat(attempt.updatedAt()).isEqualTo(after);
    }

    @Test
    void successResets() {
        AccessAttempt attempt = AccessAttempt.create(Channel.WEB, "origin", T0);
        attempt.registerFailure(T0, 5, FIFTEEN);
        attempt.reset(T0.plusSeconds(1));
        assertThat(attempt.failedAttempts()).isZero();
        assertThat(attempt.blockedUntil()).isNull();
        assertThat(attempt.lockEnded(T0)).isFalse();
        assertThat(attempt.id()).isNotNull();
    }
}
