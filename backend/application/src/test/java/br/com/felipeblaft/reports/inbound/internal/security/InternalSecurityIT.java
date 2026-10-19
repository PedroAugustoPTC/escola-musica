package br.com.felipeblaft.reports.inbound.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V4/SC-005: matriz de credenciais da área {@code /interno/**}. Só "token de serviço correto na
 * área interna" autentica; tudo o mais é {@code 401} <strong>sem corpo</strong>. O token de serviço
 * não abre o portal e o cookie do portal não abre a área interna; o CSRF do portal continua
 * exigido.
 */
@AutoConfigureMockMvc
class InternalSecurityIT extends IntegrationBase {

    static final String ROUTE = "/internal/v1/channel/telegram/actions/missing";
    static final String CURRENT = "current-service-token-test";
    static final String NEXT = "next-service-token-test";

    @Autowired MockMvc mvc;
    @Autowired ScenarioIT scenario;

    private MockHttpServletRequestBuilder route(String token) {
        MockHttpServletRequestBuilder request = get(ROUTE);
        return token == null ? request : request.header("X-N8N-Service-Token", token);
    }

    private void requires401WithoutBody(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private Cookie claraSession() throws Exception {
        scenario.clara();
        MvcResult input =
                mvc.perform(
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("session");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    @Test
    void withoutTokenOrWrongTokenAreRefusedWithoutBody() throws Exception {
        requires401WithoutBody(route(null));
        requires401WithoutBody(route("wrong"));
        requires401WithoutBody(route(""));
        requires401WithoutBody(route(CURRENT + "x"));
        requires401WithoutBody(route(CURRENT.substring(0, CURRENT.length() - 1)));
    }

    @Test
    void currentAndNextTokenBothAuthenticateWhenConfigured() throws Exception {
        // autenticado: chega ao controlador, que responde 404 (ação desconhecida), nunca 401
        assertThat(status(route(CURRENT))).isEqualTo(404);
        assertThat(status(route(NEXT))).isEqualTo(404);
    }

    @Test
    void doesNotAuthenticateByOtherHeaderNorByParameter() throws Exception {
        requires401WithoutBody(get(ROUTE).header("Authorization", "Bearer " + CURRENT));
        requires401WithoutBody(get(ROUTE).param("token", CURRENT));
        requires401WithoutBody(get(ROUTE).header("x-n8n-service-token-x", CURRENT));
    }

    @Test
    void onlyPortalSessionCookieDoesNotOpenInternalArea() throws Exception {
        Cookie session = claraSession();
        requires401WithoutBody(get(ROUTE).cookie(session));
        requires401WithoutBody(get(ROUTE).cookie(session).header("X-N8N-Service-Token", "wrong"));
        // o cookie não atrapalha quem tem o token certo
        assertThat(status(get(ROUTE).cookie(session).header("X-N8N-Service-Token", CURRENT)))
                .isEqualTo(404);
    }

    @Test
    void serviceTokenDoesNotOpenPortal() throws Exception {
        MvcResult result =
                mvc.perform(get("/api/v1/home").header("X-N8N-Service-Token", CURRENT)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        MvcResult withCookieAndWrongToken =
                mvc.perform(
                                get("/api/v1/home")
                                        .header("X-N8N-Service-Token", "wrong")
                                        .cookie(new Cookie("session", "invalid")))
                        .andReturn();
        assertThat(withCookieAndWrongToken.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void serviceTokenDoesNotReplaceSessionNorOnMutablePortalRoute() throws Exception {
        MvcResult result =
                mvc.perform(
                                delete("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .header("X-N8N-Service-Token", CURRENT))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void portalCsrfStaysRequiredAndInternalChainDoesNotRequireIt() throws Exception {
        // portal: POST mutável sem token CSRF é recusado
        assertThat(
                        status(
                                post("/api/v1/session")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"X\"}")))
                .isEqualTo(403);
        // interno: POST sem CSRF, com token de serviço, chega ao controlador (400 por corpo
        // ausente)
        assertThat(
                        status(
                                post("/internal/v1/channel/telegram/confirmations")
                                        .header("X-N8N-Service-Token", CURRENT)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}")))
                .isEqualTo(400);
        // interno sem token: 401 mesmo sem CSRF (e nunca 403)
        MvcResult withoutToken =
                mvc.perform(
                                post("/internal/v1/channel/telegram/confirmations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(withoutToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(withoutToken.getResponse().getContentAsByteArray()).isEmpty();
    }

    /**
     * V3, US2.6, C8: além do token de serviço, /atualizacao exige o segredo do webhook do Telegram.
     */
    @Test
    void updateRequiresWebhookSecretBesidesServiceToken() throws Exception {
        String path = "/internal/v1/channel/telegram/update";
        for (String secret : new String[] {null, "", "wrong", "secret-webhook-test-x"}) {
            MockHttpServletRequestBuilder request =
                    post(path)
                            .header("X-N8N-Service-Token", CURRENT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}");
            if (secret != null) {
                request.header("X-Telegram-Bot-Api-Secret-Token", secret);
            }
            requires401WithoutBody(request);
        }
        // segredo certo mas sem token de serviço: também 401
        requires401WithoutBody(
                post(path)
                        .header("X-Telegram-Bot-Api-Secret-Token", "secret-webhook-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"));
        // os dois certos: 200 (corpo vazio de updates é só ignorado)
        MvcResult ok =
                mvc.perform(
                                post(path)
                                        .header("X-N8N-Service-Token", NEXT)
                                        .header(
                                                "X-Telegram-Bot-Api-Secret-Token",
                                                "secret-webhook-test")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void missingInternalRouteWithoutTokenIsAlso401() throws Exception {
        requires401WithoutBody(get("/internal/v1/any/thing"));
        requires401WithoutBody(get("/internal"));
        requires401WithoutBody(get("/internal/"));
    }
}
