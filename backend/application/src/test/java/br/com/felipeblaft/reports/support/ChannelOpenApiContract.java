package br.com.felipeblaft.reports.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Valida rotas, métodos e códigos HTTP das respostas da API interna contra {@code
 * canal-telegram-interno.openapi.yaml} (os corpos têm a validação normativa própria em {@link
 * ActionsSchemaV1}). A ausência do token de serviço é justamente o cenário de {@code 401}.
 */
public final class ChannelOpenApiContract {

    private static final OpenApiInteractionValidator VALIDATOR =
            OpenApiInteractionValidator.createForSpecificationUrl(
                            ActionsSchemaV1.contract("telegram-channel-internal.openapi.yaml")
                                    .toUri()
                                    .toString())
                    .withLevelResolver(
                            LevelResolver.create()
                                    .withLevel(
                                            "validation.request.security.missing",
                                            ValidationReport.Level.IGNORE)
                                    .withLevel(
                                            "validation.request.parameter.header.missing",
                                            ValidationReport.Level.IGNORE)
                                    .build())
                    .build();

    private ChannelOpenApiContract() {}

    /** A resposta (status, tipo de conteúdo e, se JSON, o corpo) segue o contrato da rota. */
    public static void requireReply(MvcResult result) throws Exception {
        MockHttpServletRequest request = result.getRequest();
        MockHttpServletResponse response = result.getResponse();
        var constructor = SimpleResponse.Builder.status(response.getStatus());
        if (response.getContentType() != null) {
            constructor.withContentType(response.getContentType());
        }
        if (response.getContentType() != null && response.getContentType().contains("json")) {
            constructor.withBody(response.getContentAsString());
        } else if (response.getContentAsByteArray().length > 0) {
            // binário (PDF): o validador só precisa saber que há corpo
            constructor.withBody(
                    new String(
                            response.getContentAsByteArray(),
                            java.nio.charset.StandardCharsets.ISO_8859_1));
        }
        ValidationReport report =
                VALIDATOR.validateResponse(
                        request.getRequestURI(),
                        Request.Method.valueOf(request.getMethod()),
                        constructor.build());
        if (report.hasErrors()) {
            throw new AssertionError(
                    "resposta de "
                            + request.getMethod()
                            + " "
                            + request.getRequestURI()
                            + " fora do contrato: "
                            + report.getMessages());
        }
    }
}
