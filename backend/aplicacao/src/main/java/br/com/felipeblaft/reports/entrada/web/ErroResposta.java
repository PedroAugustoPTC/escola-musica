package br.com.felipeblaft.reports.entrada.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Corpo de erro da API (schema {@code Erro} do contrato). Nunca expõe detalhe interno (FR-054). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResposta(
        String codigo, String mensagem, Integer retryAfterSeconds, Map<String, Object> detalhes) {}
