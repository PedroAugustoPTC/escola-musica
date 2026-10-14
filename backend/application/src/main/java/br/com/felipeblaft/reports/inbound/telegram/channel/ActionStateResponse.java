package br.com.felipeblaft.reports.inbound.telegram.channel;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corpo 200 de {@code GET /acoes/{acaoId}}: o estado que permite ao receptor pular uma execução.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActionStateResponse(
        String versao, String acaoId, String estado, String codigoMotivo) {}
