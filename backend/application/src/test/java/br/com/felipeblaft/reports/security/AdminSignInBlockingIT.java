package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * SC-008, ponta a ponta (Postgres real, relógio controlado, casos de uso e cadeia de segurança
 * reais): depois de 5 senhas erradas, as tentativas seguintes são recusadas por 15 minutos mesmo
 * com a senha certa, informando o tempo restante; o fim do bloqueio zera a contagem; e a sessão
 * deixa de valer 2 horas após o login, sem renovação, em todos os casos testados.
 */
class AdminSignInBlockingIT extends AdministrationStackBase {

    private static final String ORIGIN = "198.51.100.20";
    private static final String OTHER_ORIGIN = "198.51.100.21";
    private static final String WRONG = "wrong-password-";

    private AdministratorFixture administrator;

    @BeforeEach
    void prepare() {
        administrator = insertAdministrator();
    }

    private MockHttpServletResponse attempt(String password, String origin) throws Exception {
        return signInAdministratorRaw(administrator.email(), password, origin);
    }

    private MockHttpServletResponse rightPassword(String origin) throws Exception {
        return attempt(administrator.password().value(), origin);
    }

    private void failFiveTimes(String origin) throws Exception {
        for (int i = 1; i <= 5; i++) {
            MockHttpServletResponse response = attempt(WRONG + i, origin);
            assertThat(response.getStatus()).as("erro %d", i).isEqualTo(401);
            assertThat(errorCode(response)).isEqualTo("INVALID_CREDENTIALS");
        }
    }

    @Test
    void sixthAttemptIsRefusedEvenWithTheRightPasswordAndShowsTheRemainingTime() throws Exception {
        failFiveTimes(ORIGIN);

        MockHttpServletResponse sixth = rightPassword(ORIGIN);

        assertThat(sixth.getStatus()).isEqualTo(429);
        assertThat(errorCode(sixth)).isEqualTo("TEMPORARILY_BLOCKED");
        assertThat(body(sixth).path("retryAfterSeconds").asInt()).isEqualTo(900);
        assertThat(sixth.getHeader("Retry-After")).isEqualTo("900");
        assertThat(sixth.getCookie(ADMIN_COOKIE)).isNull();
    }

    @Test
    void theBlockIsKeptForFifteenMinutesWithoutBeingExtendedByFailuresInsideIt() throws Exception {
        failFiveTimes(ORIGIN);

        clock.advance(Duration.ofMinutes(10));
        MockHttpServletResponse inside = rightPassword(ORIGIN);
        assertThat(inside.getStatus()).isEqualTo(429);
        assertThat(body(inside).path("retryAfterSeconds").asInt()).isEqualTo(300);

        // falha dentro do bloqueio não conta nem prorroga
        assertThat(attempt(WRONG + "inside", ORIGIN).getStatus()).isEqualTo(429);
        clock.advance(Duration.ofSeconds(299));
        MockHttpServletResponse lastSecond = rightPassword(ORIGIN);
        assertThat(lastSecond.getStatus()).isEqualTo(429);
        assertThat(body(lastSecond).path("retryAfterSeconds").asInt()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(rightPassword(ORIGIN).getStatus()).isEqualTo(200);
    }

    @Test
    void theEndOfTheBlockResetsTheCount() throws Exception {
        failFiveTimes(ORIGIN);
        clock.advance(Duration.ofMinutes(15));

        // quatro erros novos não bloqueiam: a contagem recomeçou do zero
        for (int i = 1; i <= 4; i++) {
            assertThat(attempt(WRONG + "new-" + i, ORIGIN).getStatus()).isEqualTo(401);
        }
        MockHttpServletResponse right = rightPassword(ORIGIN);
        assertThat(right.getStatus()).isEqualTo(200);

        // o sucesso zerou: mais quatro erros ainda não bloqueiam
        for (int i = 1; i <= 4; i++) {
            assertThat(attempt(WRONG + "after-" + i, ORIGIN).getStatus()).isEqualTo(401);
        }
        assertThat(rightPassword(ORIGIN).getStatus()).isEqualTo(200);
    }

    @Test
    void theBlockIsPerOriginAndDoesNotAffectAnotherOne() throws Exception {
        failFiveTimes(ORIGIN);

        assertThat(rightPassword(ORIGIN).getStatus()).isEqualTo(429);
        assertThat(rightPassword(OTHER_ORIGIN).getStatus()).isEqualTo(200);
    }

    @Test
    void theBlockedAttemptsAreRecordedAndTheTypedPasswordsAreNot() throws Exception {
        failFiveTimes(ORIGIN);
        rightPassword(ORIGIN);

        assertThat(authorship("ADMIN_SIGN_IN"))
                .extracting(row -> row.get("result"))
                .containsExactly("FAILURE", "FAILURE", "FAILURE", "FAILURE", "FAILURE", "BLOCKED");
        assertThat(authorshipDump())
                .doesNotContain(WRONG)
                .doesNotContain(administrator.password().value());
    }

    @Test
    void theSessionStopsWorkingTwoHoursAfterTheLoginInEveryCaseAndIsNeverRenewed()
            throws Exception {
        Instant start = clock.now();
        for (int i = 0; i < 5; i++) {
            Instant loginAt = start.plus(Duration.ofMinutes(17L * i));
            clock.define(loginAt);
            Cookie session = signInAdministrator(administrator, "198.51.100." + (50 + i));

            assertThat(sessionStatus(session)).as("logo após o login").isEqualTo(200);
            // atividade no meio da validade não renova a sessão
            clock.define(loginAt.plus(Duration.ofHours(1)));
            assertThat(sessionStatus(session)).as("depois de 1 h").isEqualTo(200);
            clock.define(loginAt.plus(Duration.ofMinutes(119)).plusSeconds(59));
            assertThat(sessionStatus(session)).as("1 s antes das 2 h").isEqualTo(200);

            clock.define(loginAt.plus(Duration.ofHours(2)));
            MockHttpServletResponse expired = call(HttpMethod.GET, ADMIN + "/session", session);
            assertThat(expired.getStatus()).as("às 2 h").isEqualTo(401);
            assertThat(errorCode(expired)).isEqualTo("SESSION_EXPIRED");
            clock.define(loginAt.plus(Duration.ofHours(5)));
            assertThat(sessionStatus(session)).as("depois das 2 h").isEqualTo(401);
        }
    }

    @Test
    void theExpiryDateReportedByTheSessionIsFixedAtTwoHours() throws Exception {
        Cookie session = signInAdministrator(administrator);
        Instant expected = clock.now().plus(Duration.ofHours(2));

        clock.advance(Duration.ofMinutes(45));
        MockHttpServletResponse current = call(HttpMethod.GET, ADMIN + "/session", session);

        assertThat(current.getStatus()).isEqualTo(200);
        assertThat(Instant.parse(body(current).path("expiresAt").asString())).isEqualTo(expected);
    }

    private int sessionStatus(Cookie session) throws Exception {
        return call(HttpMethod.GET, ADMIN + "/session", session).getStatus();
    }
}
