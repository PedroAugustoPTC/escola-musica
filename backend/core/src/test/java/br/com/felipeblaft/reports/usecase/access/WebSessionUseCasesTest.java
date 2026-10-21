package br.com.felipeblaft.reports.usecase.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.usecase.teacher.TeacherScenario;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** ValidateSessionWebUseCase e EndWebSessionUseCase. */
class WebSessionUseCasesTest {

    private final TeacherScenario c = new TeacherScenario();
    private final SignInPortalUseCase signIn =
            new SignInPortalUseCase(
                    c.authentication,
                    c.renewal,
                    c.sessions,
                    c.links,
                    c.protection,
                    c.authorship,
                    c.clock,
                    c.parameters);
    private final ValidateSessionWebUseCase validate =
            new ValidateSessionWebUseCase(
                    c.sessions, c.teachers, c.renewal, c.protection, c.clock, c.parameters);
    private final EndWebSessionUseCase end = new EndWebSessionUseCase(c.sessions, c.protection);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");

    private static void failWith(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }

    @Test
    void validSessionIdentifiesTeacherAndRenewsSliding() {
        String token = signIn.run("K7QM2XPA", "ip").token();
        c.clock.advance(Duration.ofHours(11));

        ValidateSessionWebUseCase.Result result = validate.run(token);

        assertThat(result.teacher().id()).isEqualTo(clara.id());
        assertThat(result.session().expiresAt())
                .isEqualTo(c.clock.now().plus(Duration.ofHours(12)));
        c.clock.advance(Duration.ofHours(11));
        assertThat(validate.run(token).teacher().id()).isEqualTo(clara.id());
    }

    @Test
    void useWithSessionRenewsCodeAndCodeExpiryDoesNotEndSession() {
        String token = signIn.run("K7QM2XPA", "ip").token();
        for (int use = 0; use < 20; use++) {
            c.clock.advance(Duration.ofHours(11));
            validate.run(token);
        }
        Teacher read = c.teachers.searchById(clara.id()).orElseThrow();
        assertThat(read.validCodeAt(c.clock.now())).isTrue();
    }

    @Test
    void withoutTokenNotAuthenticated() {
        failWith(() -> validate.run(null), ErrorCode.NOT_AUTHENTICATED);
        failWith(() -> validate.run(" "), ErrorCode.NOT_AUTHENTICATED);
    }

    @Test
    void unknownOrExpiredToken() {
        failWith(() -> validate.run("invented"), ErrorCode.SESSION_EXPIRED);
        String token = signIn.run("K7QM2XPA", "ip").token();
        c.clock.advance(Duration.ofHours(12));
        failWith(() -> validate.run(token), ErrorCode.SESSION_EXPIRED);
        assertThat(c.sessions.all()).isEmpty();
    }

    @Test
    void sessionRevokedByInactivationEndsWithAccessEndedAndIsKept() {
        String token = signIn.run("K7QM2XPA", "ip").token();
        WebSession session = c.sessions.all().getFirst();
        session.revoke(SessionRevocationReason.ACCOUNT_INACTIVATED, c.clock.now());
        clara.inactivate(c.clock.now());

        failWith(() -> validate.run(token), ErrorCode.ACCESS_ENDED);
        failWith(() -> validate.run(token), ErrorCode.ACCESS_ENDED);

        assertThat(c.sessions.all()).hasSize(1);
    }

    @Test
    void sessionRevokedByReissueIsSessionExpired() {
        String token = signIn.run("K7QM2XPA", "ip").token();
        c.sessions.all().getFirst().revoke(SessionRevocationReason.CODE_REISSUED, c.clock.now());

        failWith(() -> validate.run(token), ErrorCode.SESSION_EXPIRED);
        failWith(() -> validate.run(token), ErrorCode.SESSION_EXPIRED);
    }

    @Test
    void revokedAndAlsoTimedOutSessionStillAnswersByTheRevocationReason() {
        String token = signIn.run("K7QM2XPA", "ip").token();
        c.sessions
                .all()
                .getFirst()
                .revoke(SessionRevocationReason.ACCOUNT_INACTIVATED, c.clock.now());
        c.clock.advance(Duration.ofHours(13));

        failWith(() -> validate.run(token), ErrorCode.ACCESS_ENDED);
    }

    @Test
    void inactiveTeacherWithAnUnmarkedSessionSeesAccessEnded() {
        String token = signIn.run("K7QM2XPA", "ip").token();
        clara.updateRegistry(clara.name(), false, c.clock.now());

        failWith(() -> validate.run(token), ErrorCode.ACCESS_ENDED);
        assertThat(c.sessions.all()).hasSize(1);
    }

    @Test
    void teacherReactivatedLaterStillHasToSignInAgain() {
        String token = signIn.run("K7QM2XPA", "ip").token();
        c.sessions
                .all()
                .getFirst()
                .revoke(SessionRevocationReason.ACCOUNT_INACTIVATED, c.clock.now());

        failWith(() -> validate.run(token), ErrorCode.ACCESS_ENDED);
        assertThat(signIn.run("K7QM2XPA", "ip").teacher().id()).isEqualTo(clara.id());
    }

    @Test
    void endRevokesOnServer() {
        String token = signIn.run("K7QM2XPA", "ip").token();
        end.run(token);
        failWith(() -> validate.run(token), ErrorCode.SESSION_EXPIRED);
        end.run(null);
        end.run("");
        end.run("unknown");
        assertThat(c.sessions.all()).isEmpty();
    }
}
