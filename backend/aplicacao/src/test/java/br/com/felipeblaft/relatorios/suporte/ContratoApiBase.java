package br.com.felipeblaft.relatorios.suporte;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base dos testes de contrato da API do portal: MockMvc + validação de requisições e respostas
 * contra contracts/api-portal.openapi.yaml (R-16).
 */
@AutoConfigureMockMvc
public abstract class ContratoApiBase extends IntegracaoBase {

    private static final OpenApiInteractionValidator VALIDADOR = criarValidador();

    @Autowired protected MockMvc mvc;

    @Autowired protected CenarioIT cenario;

    static String caminhoContrato() {
        String configurado = System.getenv("CONTRATO_API");
        if (configurado != null && Files.exists(Path.of(configurado))) {
            return configurado;
        }
        return Path.of("../../specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml")
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    private static OpenApiInteractionValidator criarValidador() {
        return OpenApiInteractionValidator.createForSpecificationUrl(
                        Path.of(caminhoContrato()).toUri().toString())
                .withLevelResolver(
                        LevelResolver.create()
                                // a ausência do cookie é justamente o cenário de 401
                                .withLevel(
                                        "validation.request.security.missing",
                                        ValidationReport.Level.IGNORE)
                                .withLevel(
                                        "validation.request.parameter.header.missing",
                                        ValidationReport.Level.IGNORE)
                                .build())
                .build();
    }

    /** A requisição e a resposta seguem o contrato OpenAPI. */
    protected static ResultMatcher segueContrato() {
        return OpenApiValidationMatchers.openApi().isValid(VALIDADOR);
    }

    /** Entra com o código e devolve o cookie de sessão. */
    protected Cookie entrar(String codigo) throws Exception {
        var resultado =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + codigo + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie cookie = resultado.getResponse().getCookie("sessao");
        if (cookie == null) {
            throw new IllegalStateException("cookie de sessão ausente");
        }
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    /** Requisição autenticada com o cookie e o token de CSRF. */
    protected static MockHttpServletRequestBuilder autenticada(
            MockHttpServletRequestBuilder requisicao, Cookie sessao) {
        return requisicao.cookie(sessao).with(csrf().asHeader());
    }
}
