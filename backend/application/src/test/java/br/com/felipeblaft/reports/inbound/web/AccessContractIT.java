package br.com.felipeblaft.reports.inbound.web;

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

import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Contrato de POST/GET/DELETE /sessao (FR-004, FR-007, FR-008). */
class AccessContractIT extends ContractApiBase {

    @BeforeEach
    void load() {
        scenario.clara();
    }

    private org.springframework.test.web.servlet.ResultActions retry(String code, String ip)
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
                        .content("{\"codigo\":\"" + code + "\"}"));
    }

    @Test
    void signInSetsSecureCookieAndReturnsSession() throws Exception {
        retry("k7qm-2xpa", "200.1.1.1")
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.professor.nome").value("Clara Souza"))
                .andExpect(jsonPath("$.telegramVinculado").value(false))
                .andExpect(header().string("Set-Cookie", containsString("sessao=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/")));
        assertThat(jdbc.queryForObject("select count(*) from web_session", Integer.class))
                .isEqualTo(1);
        String hash = jdbc.queryForObject("select token_hash from web_session", String.class);
        assertThat(hash).hasSize(64);
    }

    @Test
    void currentSessionAndEnding() throws Exception {
        Cookie session = signIn(ScenarioIT.CODE_CLARA);

        mvc.perform(get("/api/v1/sessao").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.professor.nome").value("Clara Souza"));

        mvc.perform(authenticated(delete("/api/v1/sessao"), session))
                .andExpect(status().isNoContent())
                .andExpect(followsContract())
                .andExpect(cookie().maxAge("sessao", 0));

        mvc.perform(get("/api/v1/sessao").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("SESSAO_EXPIRADA"));
    }

    @Test
    void withoutCookieAndNotAuthenticated() throws Exception {
        mvc.perform(get("/api/v1/sessao"))
                .andExpect(status().isUnauthorized())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
                .andExpect(
                        jsonPath("$.mensagem")
                                .value("Entre com o seu código de acesso para continuar."));
    }

    @Test
    void sessionExpiresAfterTwelveHoursWithoutUse() throws Exception {
        Cookie session = signIn(ScenarioIT.CODE_CLARA);
        clock.advance(Duration.ofHours(12));
        mvc.perform(get("/api/v1/inicio").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_EXPIRADA"));
    }

    @Test
    void invalidCodeAndLockWithRetryAfter() throws Exception {
        retry("ZZZZZZZZ", "200.1.1.9")
                .andExpect(status().isUnauthorized())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
        for (int i = 0; i < 4; i++) {
            retry("ZZZZZZZZ", "200.1.1.9").andExpect(status().isUnauthorized());
        }
        retry(ScenarioIT.CODE_CLARA, "200.1.1.9")
                .andExpect(status().isTooManyRequests())
                .andExpect(followsContract())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.codigo").value("BLOQUEADO_TEMPORARIAMENTE"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(900))
                .andExpect(jsonPath("$.mensagem").value(containsString("15 minuto(s)")));
        // outra origem segue livre
        retry(ScenarioIT.CODE_CLARA, "200.1.1.10").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(15));
        retry(ScenarioIT.CODE_CLARA, "200.1.1.9").andExpect(status().isOk());
    }

    @Test
    void expiredCodeAndInvalidBody() throws Exception {
        clock.advance(Duration.ofDays(7));
        retry(ScenarioIT.CODE_CLARA, "200.1.1.3")
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
    void withoutCsrfChangeIsRefused() throws Exception {
        mvc.perform(
                        post("/api/v1/sessao")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"codigo\":\"K7QM2XPA\"}"))
                .andExpect(status().isForbidden());
    }
}
