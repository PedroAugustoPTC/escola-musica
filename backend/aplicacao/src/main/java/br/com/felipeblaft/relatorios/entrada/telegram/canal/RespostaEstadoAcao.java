package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Corpo 200 de {@code GET /acoes/{acaoId}}: o estado que permite ao receptor pular uma execução. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RespostaEstadoAcao(String versao, String acaoId, String estado, String codigoMotivo) {}
