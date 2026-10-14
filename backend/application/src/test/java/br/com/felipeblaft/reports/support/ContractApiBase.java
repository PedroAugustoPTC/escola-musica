package br.com.felipeblaft.reports.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Base dos testes de contrato da API do portal: MockMvc + validação de requisições e respostas
 * contra contracts/api-portal.openapi.yaml (R-16).
 */
@AutoConfigureMockMvc
public abstract class ContractApiBase extends IntegrationBase {

    private static final OpenApiInteractionValidator VALIDATOR = createValidator();

    @Autowired protected MockMvc mvc;

    @Autowired protected ScenarioIT scenario;

    static String contractPath() {
        String configured = System.getenv("CONTRATO_API");
        if (configured != null && Files.exists(Path.of(configured))) {
            return configured;
        }
        return Path.of("../../specs/001-fluxo-relatorios/contracts/api-portal.openapi.yaml")
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    private static OpenApiInteractionValidator createValidator() {
        return OpenApiInteractionValidator.createForSpecificationUrl(
                        Path.of(contractPath()).toUri().toString())
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
    /**
     * Só a resposta segue o contrato: para envios multipart, cujo corpo o validador não lê do
     * MockMvc (o formato do multipart é coberto pelos próprios testes).
     */
    protected static ResultMatcher replyFollowsContract() {
        return result -> {
            var request = result.getRequest();
            var response = result.getResponse();
            var constructor = SimpleResponse.Builder.status(response.getStatus());
            if (response.getContentType() != null) {
                constructor.withContentType(response.getContentType());
            }
            if (response.getContentType() != null && response.getContentType().contains("json")) {
                constructor.withBody(response.getContentAsString());
            }
            ValidationReport report =
                    VALIDATOR.validateResponse(
                            request.getRequestURI(),
                            Request.Method.valueOf(request.getMethod()),
                            constructor.build());
            if (report.hasErrors()) {
                throw new AssertionError("resposta fora do contrato: " + report.getMessages());
            }
        };
    }

    protected static ResultMatcher followsContract() {
        return OpenApiValidationMatchers.openApi().isValid(VALIDATOR);
    }

    /** Entra com o código e devolve o cookie de sessão. */
    protected Cookie signIn(String code) throws Exception {
        var result =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + code + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie cookie = result.getResponse().getCookie("sessao");
        if (cookie == null) {
            throw new IllegalStateException("cookie de sessão ausente");
        }
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    /** Requisição autenticada com o cookie e o token de CSRF. */
    protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B authenticated(
            B request, Cookie session) {
        return request.cookie(session).with(csrf().asHeader());
    }
}
