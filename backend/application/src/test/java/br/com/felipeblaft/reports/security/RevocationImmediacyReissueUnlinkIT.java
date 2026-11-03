package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.ImmediacyProbe;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * SC-010, reemissão do código e desvinculação do Telegram (FR-030, FR-031, FR-047): o efeito é
 * imediato. A reemissão encerra as sessões Web e, por opção, o vínculo; a desvinculação encerra o
 * vínculo, descarta as ações pendentes do chat e exige um código válido para vincular de novo.
 */
class RevocationImmediacyReissueUnlinkIT extends AdministrationStackBase {

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

    private MockHttpServletResponse reissue(boolean endTelegramLink) throws Exception {
        MockHttpServletResponse response =
                call(
                        HttpMethod.POST,
                        ADMIN + "/teachers/" + teacher.id() + "/access-code",
                        administrator,
                        Map.of("endTelegramLink", endTelegramLink));
        assertThat(response.getStatus()).isEqualTo(200);
        return response;
    }

    private MockHttpServletResponse unlink() throws Exception {
        MockHttpServletResponse response =
                call(
                        HttpMethod.DELETE,
                        ADMIN + "/teachers/" + teacher.id() + "/telegram-link",
                        administrator);
        assertThat(response.getStatus()).isEqualTo(204);
        return response;
    }

    private int homeStatus() throws Exception {
        return call(HttpMethod.GET, "/api/v1/home", oldSession).getStatus();
    }

    private boolean botServesTheTeacher() throws Exception {
        return !asksForTheAccessCode(botReplyTo(USER_CLARA, "início"));
    }

    // ------------------------------------------------------------------ reemissão

    @Test
    void reissueEndsTheWebSessionAndTheOldCodeButKeepsTheLinkWhenNotAsked() throws Exception {
        assertThat(homeStatus()).isEqualTo(200);

        MockHttpServletResponse reissued = reissue(false);

        MockHttpServletResponse web = call(HttpMethod.GET, "/api/v1/home", oldSession);
        assertThat(web.getStatus()).isEqualTo(401);
        assertThat(errorCode(web)).isEqualTo("SESSION_EXPIRED");
        assertThat(signInTeacherRaw(teacher.accessCode(), "203.0.113.40").getStatus())
                .isEqualTo(401);
        // sem a opção, o vínculo do Telegram continua valendo
        assertThat(telegramLinks()).isEqualTo(1);
        assertThat(botServesTheTeacher()).isTrue();
        // o código novo abre uma sessão nova
        String newCode = body(reissued).path("accessCode").asString();
        assertThat(newCode).isNotEqualTo(teacher.accessCode());
        Cookie fresh = signInTeacher(newCode);
        assertThat(call(HttpMethod.GET, "/api/v1/home", fresh).getStatus()).isEqualTo(200);
    }

    @Test
    void reissueEndingTheLinkAlsoCutsTheTelegramAtOnceAndDiscardsPendingActions() throws Exception {
        insertPendingAction("pending-reissue", USER_CLARA, teacher.id());

        reissue(true);

        assertThat(homeStatus()).isEqualTo(401);
        assertThat(telegramLinks()).isZero();
        assertThat(botServesTheTeacher()).isFalse();
        assertThat(stateOfAction("pending-reissue")).isEqualTo("DISCARDED");
    }

    @Test
    void noWebRequestStartedAfterTheReissueIsAccepted() throws Exception {
        try (ImmediacyProbe probe = new ImmediacyProbe(() -> homeStatus() == 200).start()) {
            reissue(false);
            probe.committed();
            probe.awaitRequestsAfter(15);

            assertThat(probe.requestsBefore()).isPositive();
            assertThat(probe.violations()).isZero();
        }
    }

    @Test
    void noTelegramUpdateStartedAfterAReissueThatEndsTheLinkIsAccepted() throws Exception {
        try (ImmediacyProbe probe = new ImmediacyProbe(this::botServesTheTeacher).start()) {
            reissue(true);
            probe.committed();
            probe.awaitRequestsAfter(5);

            assertThat(probe.requestsBefore()).isPositive();
            assertThat(probe.violations()).isZero();
        }
    }

    // ------------------------------------------------------------------ desvinculação

    @Test
    void unlinkEndsTheLinkAtOnceDiscardsPendingActionsAndKeepsTheWebSession() throws Exception {
        insertPendingAction("pending-unlink-1", USER_CLARA, teacher.id());
        insertPendingAction("pending-unlink-2", USER_CLARA, teacher.id());
        insertPendingAction("pending-other-chat", USER_BRUNO, teacher.id());

        unlink();

        assertThat(telegramLinks()).isZero();
        assertThat(botServesTheTeacher()).isFalse();
        assertThat(stateOfAction("pending-unlink-1")).isEqualTo("DISCARDED");
        assertThat(stateOfAction("pending-unlink-2")).isEqualTo("DISCARDED");
        assertThat(stateOfAction("pending-other-chat")).isEqualTo("PENDING");
        // a desvinculação é só do Telegram: a sessão Web segue valendo
        assertThat(homeStatus()).isEqualTo(200);
    }

    @Test
    void afterTheUnlinkOnlyAValidAccessCodeLinksAgain() throws Exception {
        unlink();

        // um texto qualquer não vincula nada
        assertThat(asksForTheAccessCode(botReplyTo(USER_CLARA, "oi, sou a Clara"))).isTrue();
        assertThat(telegramLinks()).isZero();
        assertThat(botReplyTo(USER_CLARA, "ZZZZZZZZ")).startsWith("Código inválido ou vencido");
        assertThat(telegramLinks()).isZero();
        // o código de acesso do professor (o mesmo, ainda válido) vincula de novo
        sendText(USER_CLARA, teacher.accessCode());
        assertThat(telegramLinks()).isEqualTo(1);
        assertThat(botServesTheTeacher()).isTrue();
    }

    @Test
    void noTelegramUpdateStartedAfterTheUnlinkIsAccepted() throws Exception {
        try (ImmediacyProbe probe = new ImmediacyProbe(this::botServesTheTeacher).start()) {
            unlink();
            probe.committed();
            probe.awaitRequestsAfter(5);

            assertThat(probe.requestsBefore()).isPositive();
            assertThat(probe.violations()).isZero();
        }
    }
}
