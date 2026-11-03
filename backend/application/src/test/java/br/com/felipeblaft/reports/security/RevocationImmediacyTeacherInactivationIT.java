package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.ImmediacyProbe;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * SC-010, inativação de professor (FR-014, FR-047): depois da conclusão da inativação, nenhuma
 * requisição nova com a sessão Web antiga nem com o vínculo do Telegram é aceita. Pilha real,
 * Postgres real; uma sonda repete a requisição antiga durante a operação.
 */
class RevocationImmediacyTeacherInactivationIT extends AdministrationStackBase {

    private Cookie administrator;
    private TeacherAccount teacher;
    private Cookie oldSession;

    @BeforeEach
    void prepare() throws Exception {
        administrator = signInAdministrator(insertAdministrator());
        teacher = createTeacher(administrator, "Clara Souza", "clara@example.test");
        oldSession = signInTeacher(teacher.accessCode());
        sendText(USER_CLARA, teacher.accessCode());
        assertThat(telegramLinks()).isEqualTo(1);
    }

    private MockHttpServletResponse inactivate() throws Exception {
        MockHttpServletResponse response =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + teacher.id() + "/inactivation",
                        administrator);
        assertThat(response.getStatus()).isEqualTo(200);
        return response;
    }

    private int homeStatus() throws Exception {
        return call(HttpMethod.GET, "/api/v1/home", oldSession).getStatus();
    }

    @Test
    void theOldWebSessionAndTheTelegramLinkWorkBeforeAndStopAfter() throws Exception {
        assertThat(homeStatus()).isEqualTo(200);
        assertThat(asksForTheAccessCode(botReplyTo(USER_CLARA, "início"))).isFalse();

        inactivate();

        MockHttpServletResponse web = call(HttpMethod.GET, "/api/v1/home", oldSession);
        assertThat(web.getStatus()).isEqualTo(401);
        assertThat(errorCode(web)).isEqualTo("ACCESS_ENDED");
        // qualquer método e rota do professor: a sessão antiga não abre nada
        assertThat(call(HttpMethod.GET, "/api/v1/my-students", oldSession).getStatus())
                .isEqualTo(401);
        assertThat(call(HttpMethod.DELETE, "/api/v1/session", oldSession).getStatus())
                .isEqualTo(401);
        assertThat(telegramLinks()).isZero();
        assertThat(asksForTheAccessCode(botReplyTo(USER_CLARA, "início"))).isTrue();
        assertThat(asksForTheAccessCode(botReplyTo(USER_CLARA, "meus alunos"))).isTrue();
    }

    @Test
    void theOldCodeStopsWorkingOnBothChannels() throws Exception {
        inactivate();

        MockHttpServletResponse web = signInTeacherRaw(teacher.accessCode(), "203.0.113.31");
        assertThat(web.getStatus()).isEqualTo(401);
        assertThat(errorCode(web)).isEqualTo("INVALID_CODE");
        String bot = botReplyTo(USER_CLARA, teacher.accessCode());
        assertThat(bot).startsWith("Código inválido ou vencido");
        assertThat(telegramLinks()).isZero();
    }

    @Test
    void reactivationDoesNotBringTheOldSessionNorTheLinkBack() throws Exception {
        inactivate();

        MockHttpServletResponse reactivation =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + teacher.id() + "/reactivation",
                        administrator);
        assertThat(reactivation.getStatus()).isEqualTo(200);

        assertThat(homeStatus()).isEqualTo(401);
        assertThat(telegramLinks()).isZero();
        assertThat(asksForTheAccessCode(botReplyTo(USER_CLARA, "início"))).isTrue();
        // só o código novo, mostrado na reativação, devolve o acesso
        String newCode = body(reactivation).path("accessCode").asString();
        assertThat(newCode).isNotEqualTo(teacher.accessCode());
        assertThat(signInTeacherRaw(teacher.accessCode(), "203.0.113.32").getStatus())
                .isEqualTo(401);
        assertThat(signInTeacher(newCode)).isNotNull();
    }

    @Test
    void noWebRequestStartedAfterTheInactivationIsAccepted() throws Exception {
        try (ImmediacyProbe probe = new ImmediacyProbe(() -> homeStatus() == 200).start()) {
            inactivate();
            probe.committed();
            probe.awaitRequestsAfter(15);

            assertThat(probe.requestsBefore()).isPositive();
            assertThat(probe.violations()).isZero();
        }
    }

    @Test
    void noTelegramUpdateStartedAfterTheInactivationIsAccepted() throws Exception {
        try (ImmediacyProbe probe =
                new ImmediacyProbe(() -> !asksForTheAccessCode(botReplyTo(USER_CLARA, "início")))
                        .start()) {
            inactivate();
            probe.committed();
            probe.awaitRequestsAfter(5);

            assertThat(probe.requestsBefore()).isPositive();
            assertThat(probe.violations()).isZero();
        }
    }
}
