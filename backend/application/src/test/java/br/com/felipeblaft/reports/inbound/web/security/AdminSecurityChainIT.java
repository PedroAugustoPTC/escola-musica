package br.com.felipeblaft.reports.inbound.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.inbound.web.admin.AdminWebBase;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorSessionResult;
import br.com.felipeblaft.reports.usecase.administrator.GetAdministratorSessionUseCase;
import br.com.felipeblaft.reports.usecase.administrator.SignInAdministratorUseCase;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Cadeia de segurança da área administrativa (FR-003, FR-005, FR-006, FR-046, FR-047, R-03, R-05,
 * R-19): cookie, CSRF em toda gravação do contrato, validade fixa sem renovação, nenhuma cache de
 * autorização e origem que não confia em cabeçalho do cliente.
 */
class AdminSecurityChainIT extends AdminWebBase {

    private static final String SIGN_IN_BODY = "{\"email\":\"a@example.test\",\"password\":\"x\"}";

    /** Toda operação de gravação do contrato sob {@code /admin}. */
    private static List<Route> writeRoutes() {
        OpenAPI contract = new OpenAPIV3Parser().read(contractPath().toString());
        List<Route> routes = new ArrayList<>();
        contract.getPaths()
                .forEach(
                        (path, item) -> {
                            if (!path.startsWith("/admin")) {
                                return;
                            }
                            item.readOperationsMap()
                                    .forEach(
                                            (method, operation) -> {
                                                if (method != PathItem.HttpMethod.GET) {
                                                    routes.add(
                                                            new Route(
                                                                    HttpMethod.valueOf(
                                                                            method.name()),
                                                                    BASE
                                                                            + path.replaceAll(
                                                                                            "\\{[^}]+}",
                                                                                            UUID.randomUUID()
                                                                                                    .toString())
                                                                                    .substring(
                                                                                            "/admin"
                                                                                                    .length())));
                                                }
                                            });
                        });
        return routes;
    }

    private record Route(HttpMethod method, String uri) {

        MockHttpServletRequestBuilder builder() {
            return MockMvcRequestBuilders.request(method, uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}");
        }
    }

    @Test
    void contractHasTheExpectedNumberOfWriteRoutes() {
        // sessão (POST, DELETE), ativação, senha, professores (5), administradores (3), alunos (4),
        // associações (2): se o contrato crescer, este teste obriga a revisar a cobertura de CSRF
        assertThat(writeRoutes()).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void everyWriteRouteRequiresTheCsrfToken() throws Exception {
        for (Route route : writeRoutes()) {
            MvcResult withoutToken =
                    mvc.perform(route.builder().cookie(adminCookie(ADMIN_TOKEN))).andReturn();
            assertThat(withoutToken.getResponse().getStatus())
                    .as("%s %s sem token de CSRF", route.method(), route.uri())
                    .isEqualTo(403);

            MvcResult wrongToken =
                    mvc.perform(
                                    route.builder()
                                            .cookie(adminCookie(ADMIN_TOKEN))
                                            .cookie(new Cookie("XSRF-TOKEN", "um-valor"))
                                            .header("X-CSRF-TOKEN", "outro-valor"))
                            .andReturn();
            assertThat(wrongToken.getResponse().getStatus())
                    .as("%s %s com token errado", route.method(), route.uri())
                    .isEqualTo(403);

            MvcResult unauthenticated = mvc.perform(route.builder()).andReturn();
            assertThat(unauthenticated.getResponse().getStatus())
                    .as("%s %s sem credencial e sem token", route.method(), route.uri())
                    .isEqualTo(403);

            MvcResult withToken =
                    mvc.perform(
                                    route.builder()
                                            .cookie(adminCookie(ADMIN_TOKEN))
                                            .with(csrf().asHeader()))
                            .andReturn();
            assertThat(withToken.getResponse().getStatus())
                    .as("%s %s com token", route.method(), route.uri())
                    .isNotEqualTo(403);
        }
    }

    @Test
    void readsDoNotNeedCsrfButNeedTheCredential() throws Exception {
        mvc.perform(get(BASE + "/teachers")).andExpect(status().isUnauthorized());
        MvcResult result =
                mvc.perform(get(BASE + "/teachers").cookie(adminCookie(ADMIN_TOKEN))).andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void validityIsFixedAndTheCookieIsNeverReissued() throws Exception {
        given(getSession.run(any(), any()))
                .willReturn(
                        new GetAdministratorSessionUseCase.Result(
                                administratorId, "Administrador", sessionExpiry, false));

        String first =
                mvc.perform(get(BASE + "/session").cookie(adminCookie(ADMIN_TOKEN)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        clock.advance(java.time.Duration.ofMinutes(90));
        MvcResult second =
                mvc.perform(get(BASE + "/session").cookie(adminCookie(ADMIN_TOKEN))).andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first);
        assertThat(second.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(value -> value.startsWith("admin_session="));
    }

    @Test
    void everyRequestValidatesTheSessionAndRevocationTakesEffectOnTheNextOne() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get(BASE + "/teachers").cookie(adminCookie(ADMIN_TOKEN)));
        }
        verify(validateSession, times(3)).run(new Secret(ADMIN_TOKEN));

        revoke(ADMIN_TOKEN, ErrorCode.ACCESS_ENDED);
        MvcResult ended =
                mvc.perform(get(BASE + "/teachers").cookie(adminCookie(ADMIN_TOKEN)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("ACCESS_ENDED"))
                        .andReturn();
        assertThat(ended.getResponse().getHeader("Set-Cookie"))
                .startsWith("admin_session=;")
                .contains("Max-Age=0")
                .contains("Path=/api/v1/admin");

        revoke(ADMIN_TOKEN, ErrorCode.SESSION_EXPIRED);
        mvc.perform(get(BASE + "/teachers").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void severalSimultaneousSessionsAreIndependent() throws Exception {
        authenticate("token-a", false);
        authenticate("token-b", false);

        for (String token : List.of("token-a", "token-b", ADMIN_TOKEN)) {
            MvcResult result =
                    mvc.perform(get(BASE + "/teachers").cookie(adminCookie(token))).andReturn();
            assertThat(result.getResponse().getStatus()).as(token).isNotIn(401, 403);
        }
        // a sessão encerrada de uma não afeta a outra
        revoke("token-a", ErrorCode.SESSION_EXPIRED);
        mvc.perform(get(BASE + "/teachers").cookie(adminCookie("token-a")))
                .andExpect(status().isUnauthorized());
        MvcResult other =
                mvc.perform(get(BASE + "/teachers").cookie(adminCookie("token-b"))).andReturn();
        assertThat(other.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void forgedForwardedForNeverChangesTheOrigin() throws Exception {
        given(signIn.run(any()))
                .willReturn(
                        new AdministratorSessionResult(
                                administratorId, "A", new Secret("t"), sessionExpiry, false));

        // de fora da rede interna: o cabeçalho do cliente é ignorado
        signInFrom("200.1.1.1", "9.9.9.9, 8.8.8.8");
        // do proxy interno (Caddy), que substitui o cabeçalho pelo endereço real
        signInFrom("10.0.0.2", "200.1.1.5");
        // sem cabeçalho: o endereço da conexão
        signInFrom("200.1.1.7", null);

        ArgumentCaptor<SignInAdministratorUseCase.Command> command =
                ArgumentCaptor.forClass(SignInAdministratorUseCase.Command.class);
        verify(signIn, times(3)).run(command.capture());
        assertThat(command.getAllValues())
                .extracting(SignInAdministratorUseCase.Command::origin)
                .containsExactly("200.1.1.1", "200.1.1.5", "200.1.1.7");
    }

    @Test
    void threeKindsOfSignInFailureGetTheSameStatusBodyAndHeaders() throws Exception {
        given(signIn.run(any())).willThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS));
        MvcResult unknown = signInFrom("200.1.1.1", null);
        MvcResult wrongPassword = signInFrom("200.1.1.2", null);
        MvcResult inactive = signInFrom("200.1.1.3", null);

        for (MvcResult other : List.of(wrongPassword, inactive)) {
            assertThat(other.getResponse().getStatus())
                    .isEqualTo(unknown.getResponse().getStatus());
            assertThat(other.getResponse().getContentAsByteArray())
                    .isEqualTo(unknown.getResponse().getContentAsByteArray());
            assertThat(other.getResponse().getHeaderNames())
                    .containsExactlyInAnyOrderElementsOf(unknown.getResponse().getHeaderNames());
        }
    }

    private MvcResult signInFrom(String remote, String forwarded) throws Exception {
        var request =
                post(BASE + "/session")
                        .with(csrf().asHeader())
                        .with(
                                r -> {
                                    r.setRemoteAddr(remote);
                                    return r;
                                })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGN_IN_BODY);
        if (forwarded != null) {
            request.header("X-Forwarded-For", forwarded);
        }
        return mvc.perform(request).andReturn();
    }
}
