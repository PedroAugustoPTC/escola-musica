package br.com.felipeblaft.reports.inbound.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.felipeblaft.reports.support.IntegrationBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-026: sem nenhum token configurado, a área interna recusa tudo (fail-closed), até o "vazio".
 */
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "reports.telegram-channel.current-token=",
            "reports.telegram-channel.next-token="
        })
class InternalSecurityWithoutTokenIT extends IntegrationBase {

    private static final String ROUTE = "/internal/v1/channel/telegram/actions/missing";

    @Autowired MockMvc mvc;

    private void requires401(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r)
            throws Exception {
        MvcResult result = mvc.perform(r).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void noConfiguredTokenRefusesAnyCredential() throws Exception {
        requires401(get(ROUTE));
        requires401(get(ROUTE).header("X-N8N-Service-Token", ""));
        requires401(get(ROUTE).header("X-N8N-Service-Token", " "));
        requires401(get(ROUTE).header("X-N8N-Service-Token", "current-service-token-test"));
        requires401(get(ROUTE).header("X-N8N-Service-Token", "any"));
    }

    @Test
    void singleConfiguredTokenDoesNotOpenOther() throws Exception {
        // contraste: este contexto não tem token algum; com token só no "próximo" ver
        // SegurancaInternaIT
        requires401(get(ROUTE).header("X-N8N-Service-Token", "next-service-token-test"));
    }
}
