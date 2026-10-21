package br.com.felipeblaft.reports.inbound.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Corpo de erro da API (schema {@code Error} do contrato). Nunca expõe detalhe interno (FR-054).
 *
 * @param reference código de referência da falha interna ({@code INTERNAL_ERROR}), o mesmo que vai
 *     ao log para o suporte achar a ocorrência; nulo nos demais erros
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Integer retryAfterSeconds,
        Map<String, Object> details,
        String reference) {

    public ErrorResponse(
            String code, String message, Integer retryAfterSeconds, Map<String, Object> details) {
        this(code, message, retryAfterSeconds, details, null);
    }
}
