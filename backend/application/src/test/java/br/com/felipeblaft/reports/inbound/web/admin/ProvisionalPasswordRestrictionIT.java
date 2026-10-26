package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorSessionResult;
import br.com.felipeblaft.reports.usecase.administrator.GetAdministratorSessionUseCase;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Restrição de ator com senha provisória (FR-034a): só a troca da senha, a leitura da sessão e a
 * saída respondem; toda outra rota administrativa é {@code 403 PASSWORD_CHANGE_REQUIRED} e nem
 * chega ao caso de uso. Uma segunda sessão aberta com a provisória não ganha acesso total.
 */
class ProvisionalPasswordRestrictionIT extends AdminWebBase {

    private static final String PROVISIONAL = "provisional-token";

    private record Route(HttpMethod method, String uri, String key) {}

    /** Todas as operações do contrato sob /admin, menos as públicas e as três permitidas. */
    private static List<Route> restrictedRoutes() {
        OpenAPI contract = new OpenAPIV3Parser().read(contractPath().toString());
        List<Route> routes = new ArrayList<>();
        contract.getPaths()
                .forEach(
                        (path, item) ->
                                item.readOperationsMap()
                                        .forEach(
                                                (method, operation) -> {
                                                    String key = method.name() + " " + path;
                                                    if (!path.startsWith("/admin")
                                                            || key.equals("POST /admin/session")
                                                            || key.equals("POST /admin/activation")
                                                            || key.equals("GET /admin/session")
                                                            || key.equals("DELETE /admin/session")
                                                            || key.equals("PUT /admin/password")) {
                                                        return;
                                                    }
                                                    String uri =
                                                            "/api/v1"
                                                                    + path.replaceAll(
                                                                            "\\{[^}]+}",
                                                                            UUID.randomUUID()
                                                                                    .toString());
                                                    routes.add(
                                                            new Route(
                                                                    HttpMethod.valueOf(
                                                                            method.name()),
                                                                    uri,
                                                                    key));
                                                }));
        return routes;
    }

    @Test
    void everyOtherAdministrativeRouteAnswers403PasswordChangeRequired() throws Exception {
        authenticate(PROVISIONAL, true);
        List<Route> routes = restrictedRoutes();
        assertThat(routes).hasSizeGreaterThanOrEqualTo(17);

        for (Route route : routes) {
            MvcResult result =
                    mvc.perform(
                                    MockMvcRequestBuilders.request(route.method(), route.uri())
                                            .cookie(adminCookie(PROVISIONAL))
                                            .with(csrf().asHeader())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{}"))
                            .andReturn();
            assertThat(result.getResponse().getStatus()).as(route.key()).isEqualTo(403);
            assertThat(result.getResponse().getContentAsString())
                    .as(route.key())
                    .contains("\"code\":\"PASSWORD_CHANGE_REQUIRED\"")
                    .contains("Defina uma nova senha para continuar.");
        }
        // a barreira da Web vem antes de qualquer caso de uso de negócio
        verifyNoInteractions(
                listTeachers,
                getTeacher,
                registerTeacher,
                updateTeacher,
                inactivateTeacher,
                reactivateTeacher,
                reissueCode,
                unlinkTelegram,
                listAdministrators,
                createAdministrator,
                inactivateAdministrator,
                reactivateAdministrator,
                listStudents,
                getStudent,
                registerStudent,
                updateStudent,
                inactivateStudent,
                reactivateStudent,
                assignTeacher,
                removeTeacher,
                previewRemoval);
    }

    @Test
    void readingTheSessionPasswordChangeAndSigningOutStayAvailable() throws Exception {
        authenticate(PROVISIONAL, true);
        given(getSession.run(any(), any()))
                .willReturn(
                        new GetAdministratorSessionUseCase.Result(
                                administratorId, "Administrador", sessionExpiry, true));
        given(changePassword.run(any(), any()))
                .willReturn(
                        new AdministratorSessionResult(
                                administratorId,
                                "Administrador",
                                new Secret("fresh"),
                                sessionExpiry,
                                false));

        mvc.perform(get(BASE + "/session").cookie(adminCookie(PROVISIONAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(
                        put(BASE + "/password")
                                .cookie(adminCookie(PROVISIONAL))
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"newPassword\":\"final-password-long-enough\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        MockMvcRequestBuilders.delete(BASE + "/session")
                                .cookie(adminCookie(PROVISIONAL))
                                .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        org.mockito.ArgumentCaptor<Actor> actor = org.mockito.ArgumentCaptor.forClass(Actor.class);
        verify(changePassword).run(actor.capture(), any());
        assertThat(actor.getValue().passwordChangeRequired()).isTrue();
    }

    @Test
    void secondSessionWithTheProvisionalPasswordIsRevokedByTheChangeAndNeverGetsFullAccess()
            throws Exception {
        authenticate("session-a", true);
        authenticate("session-b", true);
        mvc.perform(get(BASE + "/teachers").cookie(adminCookie("session-b")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        // a troca na sessão A revoga as outras e abre uma sessão nova sem restrição
        revoke("session-b", ErrorCode.SESSION_EXPIRED);
        authenticate("session-fresh", false);

        mvc.perform(get(BASE + "/teachers").cookie(adminCookie("session-b")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        MvcResult fresh =
                mvc.perform(get(BASE + "/teachers").cookie(adminCookie("session-fresh"))).andReturn();
        assertThat(fresh.getResponse().getStatus()).isNotIn(401, 403);
    }
}
