package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Revogação das sessões do professor (feature 005, R-08, migração V14): a sessão é marcada com o
 * motivo e nunca removida; só as ainda válidas são marcadas; a expiração deslizante não muda.
 */
class WebSessionRepositoryRevocationIT extends IntegrationBase {

    @Autowired WebSessionRepository sessions;
    @Autowired TeacherRepository teachers;

    private Teacher clara;
    private Teacher bruno;
    private final Instant now = Instant.parse("2026-09-21T13:00:00Z");

    @BeforeEach
    void seed() {
        clara = Teacher.create("Clara", "clara@example.test", true, now);
        bruno = Teacher.create("Bruno", "bruno@example.test", true, now);
        teachers.save(clara);
        teachers.save(bruno);
    }

    private void insertSession(Teacher owner, int n, Instant expiresAt) {
        jdbc.update(
                "insert into web_session (id, teacher_id, token_hash, created_at, last_used_at,"
                        + " expires_at) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                owner.id(),
                String.format("%064x", n),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(expiresAt));
    }

    private String reasonOf(int n) {
        return jdbc.queryForObject(
                "select revocation_reason from web_session where token_hash = ?",
                String.class,
                String.format("%064x", n));
    }

    private Instant revokedAtOf(int n) {
        java.sql.Timestamp stamp =
                jdbc.queryForObject(
                        "select revoked_at from web_session where token_hash = ?",
                        java.sql.Timestamp.class,
                        String.format("%064x", n));
        return stamp == null ? null : stamp.toInstant();
    }

    @Test
    void migrationAddsTheRevocationColumnsAsNullable() {
        assertThat(
                        jdbc.queryForList(
                                "select column_name from information_schema.columns"
                                        + " where table_name = 'web_session' and is_nullable = 'YES'",
                                String.class))
                .contains("revoked_at", "revocation_reason");
    }

    @Test
    void revokeAllOfMarksOnlyTheStillValidSessionsOfThatTeacherWithTheReason() {
        insertSession(clara, 1, now.plus(12, ChronoUnit.HOURS));
        insertSession(clara, 2, now.plus(3, ChronoUnit.HOURS));
        insertSession(clara, 3, now.minusSeconds(1)); // já vencida
        insertSession(bruno, 4, now.plus(12, ChronoUnit.HOURS));
        Instant at = now.plusSeconds(60);

        int revoked =
                sessions.revokeAllOf(clara.id(), SessionRevocationReason.ACCOUNT_INACTIVATED, at);

        assertThat(revoked).isEqualTo(2);
        assertThat(reasonOf(1)).isEqualTo("ACCOUNT_INACTIVATED");
        assertThat(reasonOf(2)).isEqualTo("ACCOUNT_INACTIVATED");
        assertThat(revokedAtOf(1)).isEqualTo(at);
        assertThat(reasonOf(3)).isNull();
        assertThat(revokedAtOf(3)).isNull();
        assertThat(reasonOf(4)).isNull();
        // marcada, nunca removida
        assertThat(jdbc.queryForObject("select count(*) from web_session", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void codeReissuedIsStoredAsItsOwnReasonAndFirstReasonWins() {
        insertSession(clara, 1, now.plus(12, ChronoUnit.HOURS));
        sessions.revokeAllOf(clara.id(), SessionRevocationReason.CODE_REISSUED, now.plusSeconds(1));

        int again =
                sessions.revokeAllOf(
                        clara.id(),
                        SessionRevocationReason.ACCOUNT_INACTIVATED,
                        now.plusSeconds(2));

        assertThat(again).isZero();
        assertThat(reasonOf(1)).isEqualTo("CODE_REISSUED");
    }

    @Test
    void revokeAllOfWithoutSessionsReturnsZero() {
        assertThat(sessions.revokeAllOf(clara.id(), SessionRevocationReason.CODE_REISSUED, now))
                .isZero();
    }

    @Test
    void databaseRejectsAReasonThatDoesNotApplyToTeachers() {
        insertSession(clara, 1, now.plus(12, ChronoUnit.HOURS));

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "update web_session set revoked_at = now(),"
                                                + " revocation_reason = 'PASSWORD_CHANGED'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removeExpiredStillDeletesExpiredSessionsAndKeepsRevokedOnesInsideTheirTerm() {
        insertSession(clara, 1, now.minusSeconds(1));
        insertSession(clara, 2, now.plus(12, ChronoUnit.HOURS));
        sessions.revokeAllOf(clara.id(), SessionRevocationReason.ACCOUNT_INACTIVATED, now);

        assertThat(sessions.removeExpired(now)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from web_session", Integer.class))
                .isEqualTo(1);
    }
}
