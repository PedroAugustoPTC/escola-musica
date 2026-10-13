package br.com.felipeblaft.reports.domain.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private final RetryPolicy policy =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    @Test
    void waitDoublesUpToCeiling() {
        long[] expectedInSeconds = {30, 60, 120, 240, 480, 900, 900, 900};
        for (int n = 1; n <= expectedInSeconds.length; n++) {
            assertThat(Duration.between(NOW, policy.nextAttempt(n, NOW)))
                    .as("tentativa %d", n)
                    .isEqualTo(Duration.ofSeconds(expectedInSeconds[n - 1]));
        }
    }

    @Test
    void veryLargeNumberOfAttemptsDoesNotOverflowCeiling() {
        assertThat(Duration.between(NOW, policy.nextAttempt(500, NOW)))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void nextAttemptWithDeadlineNeverExceedsDeadline() {
        Instant deadline = NOW.plusSeconds(100);
        assertThat(policy.nextAttempt(1, NOW, deadline)).isEqualTo(NOW.plusSeconds(30));
        assertThat(policy.nextAttempt(3, NOW, deadline)).isEqualTo(deadline);
        assertThat(policy.nextAttempt(6, NOW.plusSeconds(99), deadline)).isEqualTo(deadline);
    }

    @Test
    void jitterAppliesUpTo10PercentUpOrDown() {
        RetryPolicy plusOne =
                new RetryPolicy(
                        Duration.ofSeconds(100),
                        Duration.ofHours(1),
                        Duration.ofHours(24),
                        () -> 1.0);
        RetryPolicy minusOne =
                new RetryPolicy(
                        Duration.ofSeconds(100),
                        Duration.ofHours(1),
                        Duration.ofHours(24),
                        () -> -1.0);
        assertThat(Duration.between(NOW, plusOne.nextAttempt(1, NOW)))
                .isEqualTo(Duration.ofSeconds(110));
        assertThat(Duration.between(NOW, minusOne.nextAttempt(1, NOW)))
                .isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void jitterOutsideIntervalIsProtected() {
        RetryPolicy exaggerated =
                new RetryPolicy(
                        Duration.ofSeconds(100),
                        Duration.ofHours(1),
                        Duration.ofHours(24),
                        () -> 50.0);
        assertThat(Duration.between(NOW, exaggerated.nextAttempt(1, NOW)))
                .isEqualTo(Duration.ofSeconds(110));
    }

    @Test
    void nullJitterBecomesZero() {
        RetryPolicy withoutJitter =
                new RetryPolicy(
                        Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24), null);
        assertThat(Duration.between(NOW, withoutJitter.nextAttempt(1, NOW)))
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void deadlineCountsFromCreation() {
        assertThat(policy.deadlineOf(NOW)).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(policy.deadline()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void invalidValuesAreRefused() {
        Duration trinta = Duration.ofSeconds(30);
        Duration hora = Duration.ofHours(1);
        assertThatThrownBy(() -> new RetryPolicy(null, hora, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(Duration.ZERO, hora, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(Duration.ofSeconds(-1), hora, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(hora, trinta, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(trinta, null, hora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(trinta, hora, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(trinta, hora, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(trinta, hora, Duration.ofSeconds(-5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.nextAttempt(0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
