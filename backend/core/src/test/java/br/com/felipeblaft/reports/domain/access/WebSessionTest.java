package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebSessionTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration TWELVE_HOURS = Duration.ofHours(12);

    @Test
    void expiresTwelveHoursAfterLastUse() {
        WebSession session = WebSession.create(UUID.randomUUID(), "hash", T0, TWELVE_HOURS);
        assertThat(session.createdAt()).isEqualTo(T0);
        assertThat(session.expiresAt()).isEqualTo(T0.plus(TWELVE_HOURS));
        assertThat(session.validAt(T0.plus(TWELVE_HOURS).minusMillis(1))).isTrue();
        assertThat(session.validAt(T0.plus(TWELVE_HOURS))).isFalse();
        assertThat(session.tokenHash()).isEqualTo("hash");
        assertThat(session.teacherId()).isNotNull();
        assertThat(session.id()).isNotNull();
    }

    @Test
    void useRenewsAtMostOncePerMinute() {
        WebSession session = WebSession.create(UUID.randomUUID(), "hash", T0, TWELVE_HOURS);
        assertThat(session.registerUse(T0.plusSeconds(59), TWELVE_HOURS)).isFalse();
        assertThat(session.expiresAt()).isEqualTo(T0.plus(TWELVE_HOURS));

        Instant after = T0.plus(Duration.ofHours(11));
        assertThat(session.registerUse(after, TWELVE_HOURS)).isTrue();
        assertThat(session.lastUsedAt()).isEqualTo(after);
        assertThat(session.expiresAt()).isEqualTo(after.plus(TWELVE_HOURS));
        assertThat(session.validAt(T0.plus(Duration.ofHours(13)))).isTrue();
    }
}
