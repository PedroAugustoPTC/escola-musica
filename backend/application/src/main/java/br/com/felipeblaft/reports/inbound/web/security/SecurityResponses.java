package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.log.ErrorReference;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.inbound.web.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/** Respostas de erro escritas pelos filtros de segurança, fora do tratamento de erros do MVC. */
final class SecurityResponses {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityResponses.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SecurityResponses() {}

    /**
     * Escreve o corpo de erro do catálogo em UTF-8, com o mesmo tipo de conteúdo que o conversor do
     * MVC usa (sem {@code charset}), para que a resposta de um filtro e a de um controlador sejam
     * iguais byte a byte.
     */
    static void write(
            HttpServletResponse response, ErrorCode code, int status, MessageCatalog catalog)
            throws IOException {
        // INTERNAL_ERROR leva o código de referência no texto, no corpo e no log; os demais não
        String reference = code == ErrorCode.INTERNAL_ERROR ? ErrorReference.newReference() : null;
        if (reference != null) {
            LOG.error("security-error reference={} status={}", reference, status);
        }
        Map<String, String> parameters =
                reference == null ? Map.of() : Map.of("reference", reference);
        ErrorResponse body =
                new ErrorResponse(
                        code.name(), catalog.text(code, parameters), null, null, reference);
        response.setStatus(status);
        response.setContentType("application/json");
        response.getOutputStream()
                .write(JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
    }
}
