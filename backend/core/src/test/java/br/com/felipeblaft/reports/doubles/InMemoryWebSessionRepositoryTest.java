package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.access.WebSession;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryWebSessionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration INACTIVITY = Duration.ofHours(8);

    private final InMemoryWebSessionRepository sessions = new InMemoryWebSessionRepository();
    private final UUID clara = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();

    private WebSession saved(UUID teacher, String tokenHash, Instant createdAt) {
        WebSession session = WebSession.create(teacher, tokenHash, createdAt, INACTIVITY);
        sessions.save(session);
        return session;
    }

    @Test
    void revokeAllOfMarksEveryValidSessionOfTheTeacherWithTheReason() {
        WebSession first = saved(clara, "h1", NOW);
        WebSession second = saved(clara, "h2", NOW);

        int revoked = sessions.revokeAllOf(clara, SessionRevocationReason.ACCOUNT_INACTIVATED, NOW);

        assertThat(revoked).isEqualTo(2);
        assertThat(sessions.revocationOf(first.id()))
                .hasValueSatisfying(
                        r -> {
                            assertThat(r.reason())
                                    .isEqualTo(SessionRevocationReason.ACCOUNT_INACTIVATED);
                            assertThat(r.at()).isEqualTo(NOW);
                        });
        assertThat(sessions.revocationOf(second.id())).isPresent();
    }

    @Test
    void revokeAllOfNeverTouchesAnotherTeachersSessions() {
        WebSession other = saved(bruno, "h1", NOW);
        saved(clara, "h2", NOW);

        sessions.revokeAllOf(clara, SessionRevocationReason.CODE_REISSUED, NOW);

        assertThat(sessions.revocationOf(other.id())).isEmpty();
        assertThat(sessions.isRevoked(other.id())).isFalse();
    }

    @Test
    void revokedSessionIsMarkedNotRemoved() {
        WebSession session = saved(clara, "h1", NOW);

        sessions.revokeAllOf(clara, SessionRevocationReason.CODE_REISSUED, NOW);

        assertThat(sessions.searchByTokenHash("h1")).contains(session);
        assertThat(sessions.all()).containsExactly(session);
        assertThat(sessions.isRevoked(session.id())).isTrue();
    }

    @Test
    void expiredSessionsAreNotRevoked() {
        WebSession expired = saved(clara, "h1", NOW.minus(Duration.ofDays(2)));
        WebSession valid = saved(clara, "h2", NOW);

        int revoked = sessions.revokeAllOf(clara, SessionRevocationReason.CODE_REISSUED, NOW);

        assertThat(revoked).isEqualTo(1);
        assertThat(sessions.isRevoked(expired.id())).isFalse();
        assertThat(sessions.isRevoked(valid.id())).isTrue();
    }

    @Test
    void alreadyRevokedSessionIsNotRevokedAgainKeepingTheFirstReason() {
        WebSession session = saved(clara, "h1", NOW);
        sessions.revokeAllOf(clara, SessionRevocationReason.ACCOUNT_INACTIVATED, NOW);

        int second =
                sessions.revokeAllOf(
                        clara, SessionRevocationReason.CODE_REISSUED, NOW.plusSeconds(60));

        assertThat(second).isZero();
        assertThat(sessions.revocationOf(session.id()))
                .hasValueSatisfying(
                        r ->
                                assertThat(r.reason())
                                        .isEqualTo(SessionRevocationReason.ACCOUNT_INACTIVATED));
    }

    @Test
    void teacherWithoutSessionsRevokesZero() {
        assertThat(sessions.revokeAllOf(clara, SessionRevocationReason.CODE_REISSUED, NOW))
                .isZero();
    }

    @Test
    void removingTheSessionAlsoForgetsItsRevocation() {
        WebSession session = saved(clara, "h1", NOW);
        sessions.revokeAllOf(clara, SessionRevocationReason.CODE_REISSUED, NOW);

        sessions.remove(session.id());

        assertThat(sessions.revocationOf(session.id())).isEmpty();
    }

    @Test
    void removeExpiredDeletesTheExpiredAndTheirRevocations() {
        WebSession expired = saved(clara, "h1", NOW.minus(Duration.ofDays(2)));
        WebSession valid = saved(clara, "h2", NOW);
        sessions.revokeAllOf(clara, SessionRevocationReason.CODE_REISSUED, NOW);

        int removed = sessions.removeExpired(NOW);

        assertThat(removed).isEqualTo(1);
        assertThat(sessions.all()).containsExactly(valid);
        assertThat(sessions.revocationOf(expired.id())).isEmpty();
    }
}
