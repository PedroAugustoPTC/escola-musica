package br.com.felipeblaft.reports.entrada.interno.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.felipeblaft.reports.suporte.IntegracaoBase;
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
            "relatorios.canal-telegram.token-atual=",
            "relatorios.canal-telegram.token-proximo="
        })
class SegurancaInternaSemTokenIT extends IntegracaoBase {

    private static final String ROTA = "/interno/v1/canal/telegram/acoes/inexistente";

    @Autowired MockMvc mvc;

    private void exige401(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r)
            throws Exception {
        MvcResult resultado = mvc.perform(r).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(resultado.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void nenhumTokenConfiguradoRecusaQualquerCredencial() throws Exception {
        exige401(get(ROTA));
        exige401(get(ROTA).header("X-N8N-Service-Token", ""));
        exige401(get(ROTA).header("X-N8N-Service-Token", " "));
        exige401(get(ROTA).header("X-N8N-Service-Token", "token-servico-atual-teste"));
        exige401(get(ROTA).header("X-N8N-Service-Token", "qualquer"));
    }

    @Test
    void soUmTokenConfiguradoNaoAbreOutro() throws Exception {
        // contraste: este contexto não tem token algum; com token só no "próximo" ver
        // SegurancaInternaIT
        exige401(get(ROTA).header("X-N8N-Service-Token", "token-servico-proximo-teste"));
    }
}
