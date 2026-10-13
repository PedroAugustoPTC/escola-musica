package br.com.felipeblaft.reports.entrada.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.ContratoApiBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Contrato de POST/GET/DELETE /sessao (FR-004, FR-007, FR-008). */
class AcessoContratoIT extends ContratoApiBase {

    @BeforeEach
    void carregar() {
        cenario.clara();
    }

    private org.springframework.test.web.servlet.ResultActions tentar(String codigo, String ip)
            throws Exception {
        return mvc.perform(
                post("/api/v1/sessao")
                        .with(csrf().asHeader())
                        .with(
                                r -> {
                                    r.setRemoteAddr("10.0.0.2");
                                    return r;
                                })
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\"}"));
    }

    @Test
    void entrarDefineCookieSeguroEDevolveASessao() throws Exception {
        tentar("k7qm-2xpa", "200.1.1.1")
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.professor.nome").value("Clara Souza"))
                .andExpect(jsonPath("$.telegramVinculado").value(false))
                .andExpect(header().string("Set-Cookie", containsString("sessao=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/")));
        assertThat(jdbc.queryForObject("select count(*) from sessao_web", Integer.class))
                .isEqualTo(1);
        String hash = jdbc.queryForObject("select token_hash from sessao_web", String.class);
        assertThat(hash).hasSize(64);
    }

    @Test
    void sessaoAtualEEncerramento() throws Exception {
        Cookie sessao = entrar(CenarioIT.CODIGO_CLARA);

        mvc.perform(get("/api/v1/sessao").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.professor.nome").value("Clara Souza"));

        mvc.perform(autenticada(delete("/api/v1/sessao"), sessao))
                .andExpect(status().isNoContent())
                .andExpect(segueContrato())
                .andExpect(cookie().maxAge("sessao", 0));

        mvc.perform(get("/api/v1/sessao").cookie(sessao))
                .andExpect(status().isUnauthorized())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("SESSAO_EXPIRADA"));
    }

    @Test
    void semCookieENaoAutenticado() throws Exception {
        mvc.perform(get("/api/v1/sessao"))
                .andExpect(status().isUnauthorized())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
                .andExpect(
                        jsonPath("$.mensagem")
                                .value("Entre com o seu código de acesso para continuar."));
    }

    @Test
    void sessaoExpiraDepoisDeDozeHorasSemUso() throws Exception {
        Cookie sessao = entrar(CenarioIT.CODIGO_CLARA);
        relogio.avancar(Duration.ofHours(12));
        mvc.perform(get("/api/v1/inicio").cookie(sessao))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_EXPIRADA"));
    }

    @Test
    void codigoInvalidoEBloqueioComRetryAfter() throws Exception {
        tentar("ZZZZZZZZ", "200.1.1.9")
                .andExpect(status().isUnauthorized())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
        for (int i = 0; i < 4; i++) {
            tentar("ZZZZZZZZ", "200.1.1.9").andExpect(status().isUnauthorized());
        }
        tentar(CenarioIT.CODIGO_CLARA, "200.1.1.9")
                .andExpect(status().isTooManyRequests())
                .andExpect(segueContrato())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.codigo").value("BLOQUEADO_TEMPORARIAMENTE"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(900))
                .andExpect(jsonPath("$.mensagem").value(containsString("15 minuto(s)")));
        // outra origem segue livre
        tentar(CenarioIT.CODIGO_CLARA, "200.1.1.10").andExpect(status().isOk());
        relogio.avancar(Duration.ofMinutes(15));
        tentar(CenarioIT.CODIGO_CLARA, "200.1.1.9").andExpect(status().isOk());
    }

    @Test
    void codigoVencidoECorpoInvalido() throws Exception {
        relogio.avancar(Duration.ofDays(7));
        tentar(CenarioIT.CODIGO_CLARA, "200.1.1.3")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CODIGO_EXPIRADO"));
        mvc.perform(
                        post("/api/v1/sessao")
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"codigo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void semCsrfAAlteracaoERecusada() throws Exception {
        mvc.perform(
                        post("/api/v1/sessao")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"codigo\":\"K7QM2XPA\"}"))
                .andExpect(status().isForbidden());
    }
}
