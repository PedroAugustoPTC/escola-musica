package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.inbound.web.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Respostas de erro escritas pelos filtros de segurança, fora do tratamento de erros do MVC. */
final class SecurityResponses {

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
        ErrorResponse body =
                new ErrorResponse(code.name(), catalog.text(code, Map.of()), null, null);
        response.setStatus(status);
        response.setContentType("application/json");
        response.getOutputStream()
                .write(JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
    }
}
