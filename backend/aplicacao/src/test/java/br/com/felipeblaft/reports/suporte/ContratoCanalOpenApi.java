package br.com.felipeblaft.reports.suporte;

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
 * EsquemaAcoesV1}). A ausência do token de serviço é justamente o cenário de {@code 401}.
 */
public final class ContratoCanalOpenApi {

    private static final OpenApiInteractionValidator VALIDADOR =
            OpenApiInteractionValidator.createForSpecificationUrl(
                            EsquemaAcoesV1.contrato("canal-telegram-interno.openapi.yaml")
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

    private ContratoCanalOpenApi() {}

    /** A resposta (status, tipo de conteúdo e, se JSON, o corpo) segue o contrato da rota. */
    public static void exigirResposta(MvcResult resultado) throws Exception {
        MockHttpServletRequest requisicao = resultado.getRequest();
        MockHttpServletResponse resposta = resultado.getResponse();
        var construtor = SimpleResponse.Builder.status(resposta.getStatus());
        if (resposta.getContentType() != null) {
            construtor.withContentType(resposta.getContentType());
        }
        if (resposta.getContentType() != null && resposta.getContentType().contains("json")) {
            construtor.withBody(resposta.getContentAsString());
        } else if (resposta.getContentAsByteArray().length > 0) {
            // binário (PDF): o validador só precisa saber que há corpo
            construtor.withBody(
                    new String(
                            resposta.getContentAsByteArray(),
                            java.nio.charset.StandardCharsets.ISO_8859_1));
        }
        ValidationReport relatorio =
                VALIDADOR.validateResponse(
                        requisicao.getRequestURI(),
                        Request.Method.valueOf(requisicao.getMethod()),
                        construtor.build());
        if (relatorio.hasErrors()) {
            throw new AssertionError(
                    "resposta de "
                            + requisicao.getMethod()
                            + " "
                            + requisicao.getRequestURI()
                            + " fora do contrato: "
                            + relatorio.getMessages());
        }
    }
}
