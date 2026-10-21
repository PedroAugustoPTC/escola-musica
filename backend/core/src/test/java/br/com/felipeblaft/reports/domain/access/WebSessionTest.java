package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void newSessionIsNotRevoked() {
        WebSession session = WebSession.create(UUID.randomUUID(), "hash", T0, TWELVE_HOURS);
        assertThat(session.revoked()).isFalse();
        assertThat(session.revokedAt()).isNull();
        assertThat(session.revocationReason()).isNull();
    }

    @Test
    void revocationMarksTheSessionWithReasonAndInstantAndEndsValidity() {
        WebSession session = WebSession.create(UUID.randomUUID(), "hash", T0, TWELVE_HOURS);
        Instant at = T0.plusSeconds(30);

        assertThat(session.revoke(SessionRevocationReason.ACCOUNT_INACTIVATED, at)).isTrue();

        assertThat(session.revoked()).isTrue();
        assertThat(session.revokedAt()).isEqualTo(at);
        assertThat(session.revocationReason())
                .isEqualTo(SessionRevocationReason.ACCOUNT_INACTIVATED);
        assertThat(session.validAt(at)).isFalse();
        assertThat(session.expiresAt()).isEqualTo(T0.plus(TWELVE_HOURS));
    }

    @Test
    void secondRevocationKeepsTheFirstMark() {
        WebSession session = WebSession.create(UUID.randomUUID(), "hash", T0, TWELVE_HOURS);
        session.revoke(SessionRevocationReason.CODE_REISSUED, T0.plusSeconds(1));

        assertThat(session.revoke(SessionRevocationReason.ACCOUNT_INACTIVATED, T0.plusSeconds(9)))
                .isFalse();

        assertThat(session.revocationReason()).isEqualTo(SessionRevocationReason.CODE_REISSUED);
        assertThat(session.revokedAt()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void sessionRestoredWithRevocationKeepsIt() {
        UUID id = UUID.randomUUID();
        WebSession session =
                new WebSession(
                        id,
                        UUID.randomUUID(),
                        "hash",
                        T0,
                        T0,
                        T0.plus(TWELVE_HOURS),
                        T0.plusSeconds(5),
                        SessionRevocationReason.CODE_REISSUED);

        assertThat(session.revoked()).isTrue();
        assertThat(session.validAt(T0.plusSeconds(6))).isFalse();
        assertThat(session.id()).isEqualTo(id);
    }

    @Test
    void revocationNeedsInstantAndReason() {
        WebSession session = WebSession.create(UUID.randomUUID(), "hash", T0, TWELVE_HOURS);
        assertThatThrownBy(() -> session.revoke(null, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> session.revoke(SessionRevocationReason.CODE_REISSUED, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void restoredRevocationMustHaveBothPartsOrNone() {
        assertThatThrownBy(
                        () ->
                                new WebSession(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "hash",
                                        T0,
                                        T0,
                                        T0.plus(TWELVE_HOURS),
                                        T0,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
