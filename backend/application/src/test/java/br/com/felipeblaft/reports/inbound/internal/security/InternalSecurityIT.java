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

    static final String ROUTE = "/interno/v1/canal/telegram/acoes/inexistente";
    static final String CURRENT = "token-servico-atual-teste";
    static final String NEXT = "token-servico-proximo-teste";

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
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("sessao");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    @Test
    void withoutTokenOrWrongTokenAreRefusedWithoutBody() throws Exception {
        requires401WithoutBody(route(null));
        requires401WithoutBody(route("errado"));
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
        requires401WithoutBody(get(ROUTE).cookie(session).header("X-N8N-Service-Token", "errado"));
        // o cookie não atrapalha quem tem o token certo
        assertThat(status(get(ROUTE).cookie(session).header("X-N8N-Service-Token", CURRENT)))
                .isEqualTo(404);
    }

    @Test
    void serviceTokenDoesNotOpenPortal() throws Exception {
        MvcResult result =
                mvc.perform(get("/api/v1/inicio").header("X-N8N-Service-Token", CURRENT))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        MvcResult withCookieAndWrongToken =
                mvc.perform(
                                get("/api/v1/inicio")
                                        .header("X-N8N-Service-Token", "errado")
                                        .cookie(new Cookie("sessao", "invalida")))
                        .andReturn();
        assertThat(withCookieAndWrongToken.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void serviceTokenDoesNotReplaceSessionNorOnMutablePortalRoute() throws Exception {
        MvcResult result =
                mvc.perform(
                                delete("/api/v1/sessao")
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
                                post("/api/v1/sessao")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"X\"}")))
                .isEqualTo(403);
        // interno: POST sem CSRF, com token de serviço, chega ao controlador (400 por corpo
        // ausente)
        assertThat(
                        status(
                                post("/interno/v1/canal/telegram/confirmacoes")
                                        .header("X-N8N-Service-Token", CURRENT)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}")))
                .isEqualTo(400);
        // interno sem token: 401 mesmo sem CSRF (e nunca 403)
        MvcResult withoutToken =
                mvc.perform(
                                post("/interno/v1/canal/telegram/confirmacoes")
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
        String path = "/interno/v1/canal/telegram/atualizacao";
        for (String secret : new String[] {null, "", "errado", "segredo-webhook-teste-x"}) {
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
                        .header("X-Telegram-Bot-Api-Secret-Token", "segredo-webhook-teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"));
        // os dois certos: 200 (corpo vazio de updates é só ignorado)
        MvcResult ok =
                mvc.perform(
                                post(path)
                                        .header("X-N8N-Service-Token", NEXT)
                                        .header(
                                                "X-Telegram-Bot-Api-Secret-Token",
                                                "segredo-webhook-teste")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void missingInternalRouteWithoutTokenIsAlso401() throws Exception {
        requires401WithoutBody(get("/interno/v1/qualquer/coisa"));
        requires401WithoutBody(get("/interno"));
        requires401WithoutBody(get("/interno/"));
    }

    @Test
    void oldWebhookStaysOutsideInternalChain() throws Exception {
        // o webhook segue autenticado só pelo segredo do cabeçalho do Telegram
        assertThat(status(post("/telegram/webhook").content("{}"))).isEqualTo(401);
        assertThat(
                        status(
                                post("/telegram/webhook")
                                        .header(
                                                "X-Telegram-Bot-Api-Secret-Token",
                                                "segredo-webhook-teste")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}")))
                .isEqualTo(200);
    }
}
