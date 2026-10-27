package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdministratorSessionTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration TWO_HOURS = Duration.ofHours(2);
    private static final UUID ADMINISTRATOR = UUID.randomUUID();

    private AdministratorSession open() {
        return AdministratorSession.open(ADMINISTRATOR, "token-hash", NOW, TWO_HOURS);
    }

    @Test
    void opensWithAFixedValidityFromTheCreation() {
        AdministratorSession session = open();

        assertThat(session.administratorId()).isEqualTo(ADMINISTRATOR);
        assertThat(session.tokenHash()).isEqualTo("token-hash");
        assertThat(session.createdAt()).isEqualTo(NOW);
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(TWO_HOURS));
        assertThat(session.revokedAt()).isNull();
        assertThat(session.revocationReason()).isNull();
    }

    @Test
    void everyOpeningGetsANewId() {
        assertThat(open().id()).isNotEqualTo(open().id());
    }

    @Test
    void validWhileBeforeTheExpiryNotRevokedAndTheAccountIsActive() {
        AdministratorSession session = open();

        assertThat(session.validAt(NOW, true)).isTrue();
        assertThat(session.validAt(NOW.plus(TWO_HOURS).minusMillis(1), true)).isTrue();
    }

    @Test
    void invalidExactlyAtTheExpiryAndAfter() {
        AdministratorSession session = open();

        assertThat(session.validAt(NOW.plus(TWO_HOURS), true)).isFalse();
        assertThat(session.validAt(NOW.plus(TWO_HOURS).plusSeconds(1), true)).isFalse();
    }

    @Test
    void invalidWhenTheAccountIsInactive() {
        assertThat(open().validAt(NOW, false)).isFalse();
    }

    @Test
    void invalidWhenRevoked() {
        AdministratorSession revoked =
                open().revoke(SessionRevocationReason.ACCOUNT_INACTIVATED, NOW.plusSeconds(60));

        assertThat(revoked.revoked()).isTrue();
        assertThat(revoked.validAt(NOW.plusSeconds(61), true)).isFalse();
    }

    @Test
    void revocationKeepsTheFixedExpiryAndRecordsTheReason() {
        AdministratorSession session = open();
        Instant revokedAt = NOW.plusSeconds(60);

        AdministratorSession revoked =
                session.revoke(SessionRevocationReason.PASSWORD_CHANGED, revokedAt);

        assertThat(revoked.expiresAt()).isEqualTo(session.expiresAt());
        assertThat(revoked.revokedAt()).isEqualTo(revokedAt);
        assertThat(revoked.revocationReason()).isEqualTo(SessionRevocationReason.PASSWORD_CHANGED);
        assertThat(revoked.id()).isEqualTo(session.id());
        assertThat(revoked.tokenHash()).isEqualTo(session.tokenHash());
        assertThat(session.revoked()).isFalse();
    }

    @Test
    void aSecondRevocationKeepsTheFirstOne() {
        AdministratorSession first =
                open().revoke(SessionRevocationReason.ACCOUNT_INACTIVATED, NOW.plusSeconds(10));

        AdministratorSession second =
                first.revoke(SessionRevocationReason.PASSWORD_CHANGED, NOW.plusSeconds(20));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void expiredAtIsTheComplementOfBeingBeforeTheExpiry() {
        AdministratorSession session = open();

        assertThat(session.expiredAt(NOW.plus(TWO_HOURS).minusMillis(1))).isFalse();
        assertThat(session.expiredAt(NOW.plus(TWO_HOURS))).isTrue();
    }

    @Test
    void refusesAValidityThatIsNotPositive() {
        assertThatThrownBy(
                        () ->
                                AdministratorSession.open(
                                        ADMINISTRATOR, "token-hash", NOW, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                AdministratorSession.open(
                                        ADMINISTRATOR, "token-hash", NOW, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revocationRequiresReasonAndInstant() {
        AdministratorSession session = open();

        assertThatThrownBy(() -> session.revoke(null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> session.revoke(SessionRevocationReason.PASSWORD_CHANGED, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void doesNotRevealTheTokenHash() {
        assertThat(open().toString()).doesNotContain("token-hash");
    }
}
