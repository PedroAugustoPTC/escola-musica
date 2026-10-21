package br.com.felipeblaft.reports.inbound.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.inbound.web.admin.AdminWebBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Credencial cruzada (FR-003, R-19): o cookie do professor numa rota administrativa e o do
 * administrador numa rota do professor recebem, byte a byte, a resposta de rota inexistente; sem
 * credencial é 401; as duas sessões coexistem no mesmo navegador sem se combinar.
 */
class CrossCredentialResponderIT extends AdminWebBase {

    private static final Cookie XSRF = new Cookie("XSRF-TOKEN", "token-fixo-do-teste");
    private Cookie teacherCookie;

    @BeforeEach
    void teacherSignsIn() throws Exception {
        scenario.clara();
        MvcResult result =
                mvc.perform(
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie issued = result.getResponse().getCookie("session");
        teacherCookie = new Cookie("session", issued.getValue());
    }

    private static Map<String, List<String>> headersOf(MockHttpServletResponse response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, response.getHeaders(name));
        }
        return headers;
    }

    private MvcResult run(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.cookie(XSRF)).andReturn();
    }

    private void assertSameAsNonexistent(MvcResult actual, MvcResult reference) throws Exception {
        assertThat(reference.getResponse().getStatus()).isEqualTo(404);
        assertThat(actual.getResponse().getStatus()).isEqualTo(404);
        assertThat(actual.getResponse().getContentAsByteArray())
                .isEqualTo(reference.getResponse().getContentAsByteArray());
        assertThat(headersOf(actual.getResponse())).isEqualTo(headersOf(reference.getResponse()));
        assertThat(actual.getResponse().getContentAsString())
                .contains("\"code\":\"RESOURCE_NOT_FOUND\"");
    }

    @Test
    void teacherCookieOnAdministrativeRoutesIsIndistinguishableFromAMissingRoute()
            throws Exception {
        MvcResult missingGet = run(get("/api/v1/__nope__").cookie(teacherCookie));
        MvcResult missingPost =
                run(post("/api/v1/__nope__").cookie(teacherCookie).with(csrf().asHeader()));
        MvcResult missingDelete =
                run(delete("/api/v1/__nope__").cookie(teacherCookie).with(csrf().asHeader()));

        assertSameAsNonexistent(run(get(BASE + "/teachers").cookie(teacherCookie)), missingGet);
        assertSameAsNonexistent(run(get(BASE + "/session").cookie(teacherCookie)), missingGet);
        assertSameAsNonexistent(
                run(
                        get(BASE + "/students/00000000-0000-4000-8000-000000000001")
                                .cookie(teacherCookie)),
                missingGet);
        assertSameAsNonexistent(
                run(
                        post(BASE + "/teachers")
                                .cookie(teacherCookie)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")),
                missingPost);
        assertSameAsNonexistent(
                run(delete(BASE + "/session").cookie(teacherCookie).with(csrf().asHeader())),
                missingDelete);
        // rota administrativa que não existe: mesma resposta
        assertSameAsNonexistent(run(get(BASE + "/__nope__").cookie(teacherCookie)), missingGet);

        // o cookie do professor nunca é usado como credencial de administrador
        verifyNoInteractions(validateSession);
    }

    @Test
    void administratorCookieOnTeacherRoutesIsIndistinguishableFromAMissingRoute() throws Exception {
        MvcResult missingGet = run(get("/api/v1/__nope__").cookie(teacherCookie));

        assertSameAsNonexistent(
                run(get("/api/v1/home").cookie(adminCookie(ADMIN_TOKEN))), missingGet);
        assertSameAsNonexistent(
                run(get("/api/v1/session").cookie(adminCookie(ADMIN_TOKEN))), missingGet);
        assertSameAsNonexistent(
                run(get("/api/v1/students").cookie(adminCookie(ADMIN_TOKEN))), missingGet);
        assertSameAsNonexistent(
                run(get("/api/v1/__nope__").cookie(adminCookie(ADMIN_TOKEN))), missingGet);
    }

    @Test
    void withoutAnyCredentialTheAnswerIs401OnBothAreas() throws Exception {
        for (String uri :
                List.of(
                        BASE + "/teachers",
                        BASE + "/session",
                        BASE + "/__nope__",
                        "/api/v1/home",
                        "/api/v1/session",
                        "/api/v1/__nope__")) {
            mvc.perform(get(uri))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        }
    }

    @Test
    void staleCookiesOfTheOtherRoleDoNotTurnIntoNotFound() throws Exception {
        // cookie do professor que já não vale: a rota administrativa pede a entrada do
        // administrador
        mvc.perform(get(BASE + "/session").cookie(new Cookie("session", "token-que-nao-existe")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        // cookie do administrador que já não vale: a rota do professor pede a entrada do professor
        mvc.perform(get("/api/v1/home").cookie(adminCookie("token-que-nao-existe")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }

    @Test
    void bothSessionsCoexistInTheSameBrowserWithoutCombining() throws Exception {
        // área administrativa: vale o cookie do administrador, o do professor é ignorado
        MvcResult admin =
                run(get(BASE + "/teachers").cookie(teacherCookie).cookie(adminCookie(ADMIN_TOKEN)));
        assertThat(admin.getResponse().getStatus()).isNotIn(401, 403, 404);
        verify(validateSession).run(new Secret(ADMIN_TOKEN));

        // área do professor: vale o cookie do professor, o do administrador é ignorado
        mvc.perform(
                        get("/api/v1/home")
                                .cookie(XSRF)
                                .cookie(teacherCookie)
                                .cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk());

        // sessão de administrador vencida e a do professor válida: 401 do administrador, não 404
        MvcResult expired =
                run(get(BASE + "/teachers").cookie(teacherCookie).cookie(adminCookie("vencido")));
        assertThat(expired.getResponse().getStatus()).isEqualTo(401);
        assertThat(expired.getResponse().getContentAsString()).contains("SESSION_EXPIRED");
    }

    @Test
    void administratorCredentialDoesNotTouchTheTeacherSessionNorViceVersa() throws Exception {
        mvc.perform(get(BASE + "/session").cookie(adminCookie(ADMIN_TOKEN)));
        verify(validateSession).run(new Secret(ADMIN_TOKEN));
        verify(validateSession, never()).run(new Secret(teacherCookie.getValue()));
        // sem a credencial do administrador a rota dele não responde ao professor
        mvc.perform(get(BASE + "/session").cookie(teacherCookie)).andExpect(status().isNotFound());
        verify(validateSession, never()).run(new Secret(teacherCookie.getValue()));
    }
}
