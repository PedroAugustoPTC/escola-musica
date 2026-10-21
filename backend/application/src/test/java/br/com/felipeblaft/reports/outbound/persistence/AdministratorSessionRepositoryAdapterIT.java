package br.com.felipeblaft.reports.outbound.persistence;

import static br.com.felipeblaft.reports.outbound.persistence.AdministratorTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Sessões do administrador no PostgreSQL real (feature 005, V9): a consulta por token já traz a
 * conta (validação por requisição, sem cache), a revogação marca e não remove, "Sair" apaga só
 * uma sessão e a varredura apaga as vencidas.
 */
class AdministratorSessionRepositoryAdapterIT extends IntegrationBase {

    private static final Duration TWO_HOURS = Duration.ofHours(2);

    @Autowired AdministratorRepository administrators;
    @Autowired AdministratorSessionRepository sessions;

    private Administrator marina;
    private Administrator bruno;

    @BeforeEach
    void seed() {
        marina = AdministratorTestData.active("Marina Alves", "marina@example.test");
        bruno = AdministratorTestData.active("Bruno Reis", "bruno@example.test");
        administrators.insertIfAbsent(marina);
        administrators.insertIfAbsent(bruno);
    }

    private AdministratorSession save(Administrator owner, int n, Instant expiresAt) {
        AdministratorSession session =
                AdministratorTestData.session(owner.id(), AdministratorTestData.tokenHash(n), expiresAt);
        sessions.save(session);
        return session;
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from administrator_session", Integer.class);
    }

    @Test
    void searchByTokenHashBringsTheSessionAndTheAccountInOneRead() {
        AdministratorSession saved = save(marina, 1, NOW.plus(TWO_HOURS));

        AdministratorSessionRepository.Found found =
                sessions.searchByTokenHash(AdministratorTestData.tokenHash(1)).orElseThrow();

        assertThat(found.session().id()).isEqualTo(saved.id());
        assertThat(found.session().administratorId()).isEqualTo(marina.id());
        assertThat(found.session().tokenHash()).isEqualTo(AdministratorTestData.tokenHash(1));
        assertThat(found.session().createdAt()).isEqualTo(AdministratorTestData.micros(NOW));
        assertThat(found.session().expiresAt())
                .isEqualTo(AdministratorTestData.micros(NOW.plus(TWO_HOURS)));
        assertThat(found.session().revokedAt()).isNull();
        assertThat(found.session().revocationReason()).isNull();
        assertThat(found.administrator().id()).isEqualTo(marina.id());
        assertThat(found.administrator().email()).isEqualTo("marina@example.test");
        assertThat(found.administrator().active()).isTrue();
    }

    @Test
    void unknownTokenFindsNothing() {
        save(marina, 1, NOW.plus(TWO_HOURS));

        assertThat(sessions.searchByTokenHash(AdministratorTestData.tokenHash(2))).isEmpty();
    }

    @Test
    void accountSituationIsReadFreshOnEveryRequest() {
        save(marina, 1, NOW.plus(TWO_HOURS));
        assertThat(
                        sessions.searchByTokenHash(AdministratorTestData.tokenHash(1))
                                .orElseThrow()
                                .administrator()
                                .active())
                .isTrue();

        administrators.save(
                new Administrator(
                        marina.id(),
                        marina.name(),
                        marina.email(),
                        false,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        marina.createdAt(),
                        NOW.plusSeconds(1)));

        assertThat(
                        sessions.searchByTokenHash(AdministratorTestData.tokenHash(1))
                                .orElseThrow()
                                .administrator()
                                .active())
                .isFalse();
    }

    @Test
    void tokenHashIsUnique() {
        save(marina, 1, NOW.plus(TWO_HOURS));
        AdministratorSession clash =
                AdministratorTestData.session(
                        bruno.id(), AdministratorTestData.tokenHash(1), NOW.plus(TWO_HOURS));

        assertThatThrownBy(() -> sessions.save(clash))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saveStoresTheRevocationOfAnExistingSession() {
        AdministratorSession session = save(marina, 1, NOW.plus(TWO_HOURS));

        sessions.save(
                new AdministratorSession(
                        session.id(),
                        session.administratorId(),
                        session.tokenHash(),
                        session.createdAt(),
                        session.expiresAt(),
                        NOW.plusSeconds(30),
                        SessionRevocationReason.PASSWORD_CHANGED));

        AdministratorSession read =
                sessions.searchByTokenHash(AdministratorTestData.tokenHash(1))
                        .orElseThrow()
                        .session();
        assertThat(read.revokedAt()).isEqualTo(AdministratorTestData.micros(NOW.plusSeconds(30)));
        assertThat(read.revocationReason()).isEqualTo(SessionRevocationReason.PASSWORD_CHANGED);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void removeDeletesOnlyThatSession() {
        AdministratorSession first = save(marina, 1, NOW.plus(TWO_HOURS));
        save(marina, 2, NOW.plus(TWO_HOURS));

        sessions.remove(first.id());

        assertThat(sessions.searchByTokenHash(AdministratorTestData.tokenHash(1))).isEmpty();
        assertThat(sessions.searchByTokenHash(AdministratorTestData.tokenHash(2))).isPresent();
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void revokeAllOfMarksOnlyTheStillValidSessionsOfThatAdministrator() {
        save(marina, 1, NOW.plus(TWO_HOURS));
        save(marina, 2, NOW.plus(TWO_HOURS));
        save(marina, 3, NOW.minusSeconds(1)); // já vencida
        save(bruno, 4, NOW.plus(TWO_HOURS));
        Instant revokedAt = NOW.plusSeconds(10);

        int revoked =
                sessions.revokeAllOf(marina.id(), SessionRevocationReason.ACCOUNT_INACTIVATED, revokedAt);

        assertThat(revoked).isEqualTo(2);
        for (int n : new int[] {1, 2}) {
            AdministratorSession session =
                    sessions.searchByTokenHash(AdministratorTestData.tokenHash(n))
                            .orElseThrow()
                            .session();
            assertThat(session.revokedAt()).isEqualTo(AdministratorTestData.micros(revokedAt));
            assertThat(session.revocationReason())
                    .isEqualTo(SessionRevocationReason.ACCOUNT_INACTIVATED);
        }
        assertThat(
                        sessions.searchByTokenHash(AdministratorTestData.tokenHash(3))
                                .orElseThrow()
                                .session()
                                .revokedAt())
                .isNull();
        assertThat(
                        sessions.searchByTokenHash(AdministratorTestData.tokenHash(4))
                                .orElseThrow()
                                .session()
                                .revokedAt())
                .isNull();
        // a sessão é marcada e nunca removida: a próxima requisição recebe a resposta certa
        assertThat(count()).isEqualTo(4);
    }

    @Test
    void revokeAllOfIsIdempotentAndKeepsTheFirstReason() {
        save(marina, 1, NOW.plus(TWO_HOURS));
        sessions.revokeAllOf(marina.id(), SessionRevocationReason.PASSWORD_CHANGED, NOW.plusSeconds(1));

        int second =
                sessions.revokeAllOf(
                        marina.id(), SessionRevocationReason.ACCOUNT_INACTIVATED, NOW.plusSeconds(2));

        assertThat(second).isZero();
        assertThat(
                        sessions.searchByTokenHash(AdministratorTestData.tokenHash(1))
                                .orElseThrow()
                                .session()
                                .revocationReason())
                .isEqualTo(SessionRevocationReason.PASSWORD_CHANGED);
    }

    @Test
    void revokeAllOfWithoutSessionsReturnsZero() {
        assertThat(
                        sessions.revokeAllOf(
                                UUID.randomUUID(),
                                SessionRevocationReason.ACCOUNT_INACTIVATED,
                                NOW))
                .isZero();
    }

    @Test
    void removeExpiredDeletesTheExpiredOnesIncludingRevokedAndKeepsTheOthers() {
        save(marina, 1, NOW.minusSeconds(1)); // vencida
        save(marina, 2, NOW); // vence exatamente agora: vencida
        save(marina, 3, NOW.plus(TWO_HOURS)); // válida
        save(bruno, 4, NOW.plus(TWO_HOURS)); // válida, depois revogada
        sessions.revokeAllOf(bruno.id(), SessionRevocationReason.ACCOUNT_INACTIVATED, NOW);

        int removed = sessions.removeExpired(NOW);

        assertThat(removed).isEqualTo(2);
        assertThat(sessions.searchByTokenHash(AdministratorTestData.tokenHash(1))).isEmpty();
        assertThat(sessions.searchByTokenHash(AdministratorTestData.tokenHash(2))).isEmpty();
        assertThat(sessions.searchByTokenHash(AdministratorTestData.tokenHash(3))).isPresent();
        // revogada mas ainda dentro do prazo continua, para a resposta "acesso encerrado"
        assertThat(sessions.searchByTokenHash(AdministratorTestData.tokenHash(4))).isPresent();
        assertThat(sessions.removeExpired(NOW)).isZero();
    }

    @Test
    void databaseRejectsAnUnknownRevocationReason() {
        AdministratorSession session = save(marina, 1, NOW.plus(TWO_HOURS));

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "update administrator_session set revoked_at = now(),"
                                                + " revocation_reason = 'OTHER' where id = ?",
                                        session.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionIsStoredOnlyAsTokenHash() {
        save(marina, 1, NOW.plus(TWO_HOURS));

        assertThat(
                        jdbc.queryForList(
                                "select column_name from information_schema.columns"
                                        + " where table_name = 'administrator_session'",
                                String.class))
                .contains("token_hash", "expires_at", "revoked_at", "revocation_reason")
                .doesNotContain("token");
    }
}
