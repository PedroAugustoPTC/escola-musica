package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Corpo enviado pelo backend ao webhook do orquestrador na entrega proativa ({@code
 * #/$defs/LoteEntrega}).
 */
public record LoteEntregaDto(
        String versao,
        String loteId,
        String origem,
        String criadoEm,
        int tentativa,
        List<JsonNode> acoes) {}
