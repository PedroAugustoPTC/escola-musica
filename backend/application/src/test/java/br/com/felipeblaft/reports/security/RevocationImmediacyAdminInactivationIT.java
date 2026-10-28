package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.AdministrationStackBase;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import br.com.felipeblaft.reports.support.ImmediacyProbe;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * SC-010, inativação de administrador (FR-033, FR-047): todas as sessões do administrador inativado
 * deixam de valer na requisição seguinte; as do outro administrador seguem. Reativar não ressuscita
 * a sessão: a conta volta pendente de ativação.
 */
class RevocationImmediacyAdminInactivationIT extends AdministrationStackBase {

    private AdministratorFixture actor;
    private AdministratorFixture target;
    private Cookie actorSession;
    private Cookie targetFirstSession;
    private Cookie targetSecondSession;

    @BeforeEach
    void prepare() throws Exception {
        actor = insertAdministrator("Ana Administradora", "ana.admin@example.test");
        target = insertAdministrator("Beto Administrador", "beto.admin@example.test");
        actorSession = signInAdministrator(actor, "198.51.100.61");
        targetFirstSession = signInAdministrator(target, "198.51.100.62");
        targetSecondSession = signInAdministrator(target, "198.51.100.63");
    }

    private MockHttpServletResponse inactivateTarget() throws Exception {
        return call(
                HttpMethod.POST,
                ADMIN + "/administrators/" + target.administrator().id() + "/inactivation",
                actorSession);
    }

    private int sessionStatus(Cookie session) throws Exception {
        return call(HttpMethod.GET, ADMIN + "/session", session).getStatus();
    }

    @Test
    void everySessionOfTheInactivatedAdministratorStopsOnTheNextRequest() throws Exception {
        assertThat(sessionStatus(targetFirstSession)).isEqualTo(200);
        assertThat(sessionStatus(targetSecondSession)).isEqualTo(200);

        MockHttpServletResponse inactivation = inactivateTarget();

        assertThat(inactivation.getStatus()).isEqualTo(200);
        for (Cookie session : new Cookie[] {targetFirstSession, targetSecondSession}) {
            MockHttpServletResponse response = call(HttpMethod.GET, ADMIN + "/session", session);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(errorCode(response)).isEqualTo("ACCESS_ENDED");
        }
        assertThat(call(HttpMethod.GET, ADMIN + "/teachers", targetFirstSession).getStatus())
                .isEqualTo(401);
        assertThat(call(HttpMethod.DELETE, ADMIN + "/session", targetSecondSession).getStatus())
                .isEqualTo(401);
        // o outro administrador não é afetado
        assertThat(sessionStatus(actorSession)).isEqualTo(200);
    }

    @Test
    void theInactivatedAdministratorCannotSignInAndReactivationDoesNotRestoreTheSession()
            throws Exception {
        inactivateTarget();

        MockHttpServletResponse refused =
                signInAdministratorRaw(target.email(), target.password().value(), "198.51.100.64");
        assertThat(refused.getStatus()).isEqualTo(401);
        assertThat(errorCode(refused)).isEqualTo("INVALID_CREDENTIALS");

        MockHttpServletResponse reactivation =
                call(
                        HttpMethod.POST,
                        ADMIN + "/administrators/" + target.administrator().id() + "/reactivation",
                        actorSession);
        assertThat(reactivation.getStatus()).isEqualTo(200);
        assertThat(sessionStatus(targetFirstSession)).isEqualTo(401);
        assertThat(sessionStatus(targetSecondSession)).isEqualTo(401);
        // a conta volta pendente de ativação: a senha antiga não entra mais
        assertThat(
                        signInAdministratorRaw(
                                        target.email(), target.password().value(), "198.51.100.65")
                                .getStatus())
                .isEqualTo(401);
    }

    @Test
    void noRequestStartedAfterTheInactivationIsAccepted() throws Exception {
        try (ImmediacyProbe probe =
                new ImmediacyProbe(() -> sessionStatus(targetFirstSession) == 200).start()) {
            assertThat(inactivateTarget().getStatus()).isEqualTo(200);
            probe.committed();
            probe.awaitRequestsAfter(15);

            assertThat(probe.requestsBefore()).isPositive();
            assertThat(probe.violations()).isZero();
        }
    }
}
