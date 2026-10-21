package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryAdministratorSessionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration TWO_HOURS = Duration.ofHours(2);

    private final InMemoryAdministratorRepository administrators =
            new InMemoryAdministratorRepository();
    private final InMemoryAdministratorSessionRepository sessions =
            new InMemoryAdministratorSessionRepository(administrators);
    private final Administrator ana = account("ana@school.test");
    private final Administrator bia = account("bia@school.test");

    private Administrator account(String email) {
        Administrator administrator =
                Administrator.first("Admin", email, "hash", Duration.ofDays(7), NOW);
        administrators.save(administrator);
        return administrator;
    }

    private AdministratorSession open(Administrator administrator, String tokenHash) {
        AdministratorSession session =
                AdministratorSession.open(administrator.id(), tokenHash, NOW, TWO_HOURS);
        sessions.save(session);
        return session;
    }

    @Test
    void findsTheSessionTogetherWithTheAccount() {
        AdministratorSession session = open(ana, "h1");

        var found = sessions.searchByTokenHash("h1").orElseThrow();

        assertThat(found.session()).isEqualTo(session);
        assertThat(found.administrator()).isEqualTo(ana);
        assertThat(sessions.searchByTokenHash("unknown")).isEmpty();
    }

    @Test
    void theSearchReflectsTheCurrentStateOfTheAccount() {
        open(ana, "h1");
        administrators.save(ana.inactivate(NOW));

        assertThat(sessions.searchByTokenHash("h1").orElseThrow().administrator().active())
                .isFalse();
    }

    @Test
    void removeDeletesOnlyThatSession() {
        AdministratorSession first = open(ana, "h1");
        AdministratorSession second = open(ana, "h2");

        sessions.remove(first.id());

        assertThat(sessions.all()).containsExactly(second);
    }

    @Test
    void revokeAllMarksOnlyTheValidSessionsOfThatAdministrator() {
        AdministratorSession mine = open(ana, "h1");
        AdministratorSession other = open(bia, "h2");
        AdministratorSession already =
                open(ana, "h3").revoke(SessionRevocationReason.ACCOUNT_INACTIVATED, NOW);
        sessions.save(already);

        int revoked =
                sessions.revokeAllOf(
                        ana.id(), SessionRevocationReason.PASSWORD_CHANGED, NOW.plusSeconds(60));

        assertThat(revoked).isEqualTo(1);
        assertThat(sessions.byId(mine.id()).orElseThrow().revocationReason())
                .isEqualTo(SessionRevocationReason.PASSWORD_CHANGED);
        assertThat(sessions.byId(other.id()).orElseThrow().revoked()).isFalse();
        assertThat(sessions.byId(already.id()).orElseThrow().revocationReason())
                .isEqualTo(SessionRevocationReason.ACCOUNT_INACTIVATED);
    }

    @Test
    void revokeAllIgnoresTheExpiredOnes() {
        open(ana, "h1");

        int revoked =
                sessions.revokeAllOf(
                        ana.id(), SessionRevocationReason.PASSWORD_CHANGED, NOW.plus(TWO_HOURS));

        assertThat(revoked).isZero();
    }

    @Test
    void removeExpiredDeletesOnlyTheExpiredOnes() {
        open(ana, "h1");
        AdministratorSession fresh =
                AdministratorSession.open(ana.id(), "h2", NOW.plusSeconds(3600), TWO_HOURS);
        sessions.save(fresh);

        int removed = sessions.removeExpired(NOW.plus(TWO_HOURS));

        assertThat(removed).isEqualTo(1);
        assertThat(sessions.all()).containsExactly(fresh);
    }

    @Test
    void listsBySessionsOfAnAdministratorAndCountsRecordings() {
        open(ana, "h1");
        open(ana, "h2");
        open(bia, "h3");

        assertThat(sessions.ofAdministrator(ana.id())).hasSize(2);
        assertThat(sessions.recordings()).isEqualTo(3);
    }
}
