package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryWebSessionRepository;
import br.com.felipeblaft.reports.usecase.teacher.NoAdministratorSessions;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Revogação "na hora": marca as sessões do professor e do administrador, sem removê-las (R-08). */
class SessionRevocationServiceTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryWebSessionRepository teacherSessions = new InMemoryWebSessionRepository();
    private final NoAdministratorSessions administratorSessions = new NoAdministratorSessions();
    private final SessionRevocationService service =
            new SessionRevocationService(teacherSessions, administratorSessions, clock);

    private WebSession sessionOf(UUID teacherId, String hash) {
        WebSession session = WebSession.create(teacherId, hash, clock.now(), Duration.ofHours(12));
        teacherSessions.save(session);
        return session;
    }

    @Test
    void revokingATeacherMarksAllHisSessionsWithTheReasonAndKeepsThem() {
        UUID clara = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();
        WebSession first = sessionOf(clara, "h1");
        WebSession second = sessionOf(clara, "h2");
        WebSession other = sessionOf(bruno, "h3");

        int revoked = service.revokeTeacher(clara, SessionRevocationReason.ACCOUNT_INACTIVATED);

        assertThat(revoked).isEqualTo(2);
        assertThat(teacherSessions.all()).hasSize(3);
        assertThat(teacherSessions.revocationOf(first.id()).orElseThrow().reason())
                .isEqualTo(SessionRevocationReason.ACCOUNT_INACTIVATED);
        assertThat(teacherSessions.revocationOf(second.id()).orElseThrow().at())
                .isEqualTo(clock.now());
        assertThat(teacherSessions.isRevoked(other.id())).isFalse();
    }

    @Test
    void revokingATeacherWithoutSessionsRevokesNothing() {
        assertThat(service.revokeTeacher(UUID.randomUUID(), SessionRevocationReason.CODE_REISSUED))
                .isZero();
    }

    @Test
    void revocationUsesTheCurrentInstantAndDoesNotRevokeTwice() {
        UUID clara = UUID.randomUUID();
        WebSession session = sessionOf(clara, "h1");
        service.revokeTeacher(clara, SessionRevocationReason.CODE_REISSUED);
        clock.advance(Duration.ofMinutes(5));

        assertThat(service.revokeTeacher(clara, SessionRevocationReason.ACCOUNT_INACTIVATED))
                .isZero();

        assertThat(teacherSessions.revocationOf(session.id()).orElseThrow().reason())
                .isEqualTo(SessionRevocationReason.CODE_REISSUED);
    }

    @Test
    void revokingAnAdministratorDelegatesWithReasonAndCurrentInstant() {
        UUID administrator = UUID.randomUUID();

        int revoked =
                service.revokeAdministrator(
                        administrator, SessionRevocationReason.ACCOUNT_INACTIVATED);

        assertThat(revoked).isEqualTo(2);
        assertThat(administratorSessions.calls())
                .containsExactly(
                        new NoAdministratorSessions.Call(
                                administrator,
                                SessionRevocationReason.ACCOUNT_INACTIVATED,
                                clock.now()));
    }

    @Test
    void administratorRevocationDoesNotTouchTeacherSessions() {
        UUID id = UUID.randomUUID();
        WebSession session = sessionOf(id, "h1");

        service.revokeAdministrator(id, SessionRevocationReason.PASSWORD_CHANGED);

        assertThat(teacherSessions.isRevoked(session.id())).isFalse();
    }
}
