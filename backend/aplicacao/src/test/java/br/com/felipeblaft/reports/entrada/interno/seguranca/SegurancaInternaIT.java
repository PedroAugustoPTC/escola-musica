package br.com.felipeblaft.reports.entrada.interno.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.IntegracaoBase;
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
class SegurancaInternaIT extends IntegracaoBase {

    static final String ROTA = "/interno/v1/canal/telegram/acoes/inexistente";
    static final String ATUAL = "token-servico-atual-teste";
    static final String PROXIMO = "token-servico-proximo-teste";

    @Autowired MockMvc mvc;
    @Autowired CenarioIT cenario;

    private MockHttpServletRequestBuilder rota(String token) {
        MockHttpServletRequestBuilder requisicao = get(ROTA);
        return token == null ? requisicao : requisicao.header("X-N8N-Service-Token", token);
    }

    private void exige401SemCorpo(MockHttpServletRequestBuilder requisicao) throws Exception {
        MvcResult resultado = mvc.perform(requisicao).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(resultado.getResponse().getContentAsByteArray()).isEmpty();
    }

    private int status(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andReturn().getResponse().getStatus();
    }

    private Cookie sessaoDeClara() throws Exception {
        cenario.clara();
        MvcResult entrada =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + CenarioIT.CODIGO_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = entrada.getResponse().getCookie("sessao");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    @Test
    void semTokenOuTokenErradoSaoRecusadosSemCorpo() throws Exception {
        exige401SemCorpo(rota(null));
        exige401SemCorpo(rota("errado"));
        exige401SemCorpo(rota(""));
        exige401SemCorpo(rota(ATUAL + "x"));
        exige401SemCorpo(rota(ATUAL.substring(0, ATUAL.length() - 1)));
    }

    @Test
    void tokenAtualEProximoAutenticamAmbosConfigurados() throws Exception {
        // autenticado: chega ao controlador, que responde 404 (ação desconhecida), nunca 401
        assertThat(status(rota(ATUAL))).isEqualTo(404);
        assertThat(status(rota(PROXIMO))).isEqualTo(404);
    }

    @Test
    void naoSeAutenticaPorOutroCabecalhoNemPorParametro() throws Exception {
        exige401SemCorpo(get(ROTA).header("Authorization", "Bearer " + ATUAL));
        exige401SemCorpo(get(ROTA).param("token", ATUAL));
        exige401SemCorpo(get(ROTA).header("x-n8n-service-token-x", ATUAL));
    }

    @Test
    void soOCookieDeSessaoDoPortalNaoAbreAAreaInterna() throws Exception {
        Cookie sessao = sessaoDeClara();
        exige401SemCorpo(get(ROTA).cookie(sessao));
        exige401SemCorpo(get(ROTA).cookie(sessao).header("X-N8N-Service-Token", "errado"));
        // o cookie não atrapalha quem tem o token certo
        assertThat(status(get(ROTA).cookie(sessao).header("X-N8N-Service-Token", ATUAL)))
                .isEqualTo(404);
    }

    @Test
    void tokenDeServicoNaoAbreOPortal() throws Exception {
        MvcResult resultado =
                mvc.perform(get("/api/v1/inicio").header("X-N8N-Service-Token", ATUAL)).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        MvcResult comCookieETokenErrado =
                mvc.perform(
                                get("/api/v1/inicio")
                                        .header("X-N8N-Service-Token", "errado")
                                        .cookie(new Cookie("sessao", "invalida")))
                        .andReturn();
        assertThat(comCookieETokenErrado.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void tokenDeServicoNaoSubstituiASessaoNemEmRotaMutavelDoPortal() throws Exception {
        MvcResult resultado =
                mvc.perform(
                                delete("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .header("X-N8N-Service-Token", ATUAL))
                        .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void csrfDoPortalContinuaExigidoEACadeiaInternaNaoOExige() throws Exception {
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
                                        .header("X-N8N-Service-Token", ATUAL)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}")))
                .isEqualTo(400);
        // interno sem token: 401 mesmo sem CSRF (e nunca 403)
        MvcResult semToken =
                mvc.perform(
                                post("/interno/v1/canal/telegram/confirmacoes")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(semToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(semToken.getResponse().getContentAsByteArray()).isEmpty();
    }

    /**
     * V3, US2.6, C8: além do token de serviço, /atualizacao exige o segredo do webhook do Telegram.
     */
    @Test
    void atualizacaoExigeOSegredoDoWebhookAlemDoTokenDeServico() throws Exception {
        String caminho = "/interno/v1/canal/telegram/atualizacao";
        for (String segredo : new String[] {null, "", "errado", "segredo-webhook-teste-x"}) {
            MockHttpServletRequestBuilder requisicao =
                    post(caminho)
                            .header("X-N8N-Service-Token", ATUAL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}");
            if (segredo != null) {
                requisicao.header("X-Telegram-Bot-Api-Secret-Token", segredo);
            }
            exige401SemCorpo(requisicao);
        }
        // segredo certo mas sem token de serviço: também 401
        exige401SemCorpo(
                post(caminho)
                        .header("X-Telegram-Bot-Api-Secret-Token", "segredo-webhook-teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"));
        // os dois certos: 200 (corpo vazio de updates é só ignorado)
        MvcResult ok =
                mvc.perform(
                                post(caminho)
                                        .header("X-N8N-Service-Token", PROXIMO)
                                        .header(
                                                "X-Telegram-Bot-Api-Secret-Token",
                                                "segredo-webhook-teste")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void rotaInternaInexistenteSemTokenTambemE401() throws Exception {
        exige401SemCorpo(get("/interno/v1/qualquer/coisa"));
        exige401SemCorpo(get("/interno"));
        exige401SemCorpo(get("/interno/"));
    }

    @Test
    void webhookAntigoContinuaForaDaCadeiaInterna() throws Exception {
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
