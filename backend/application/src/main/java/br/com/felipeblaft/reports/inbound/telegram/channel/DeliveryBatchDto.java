package br.com.felipeblaft.reports.inbound.telegram.channel;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Corpo enviado pelo backend ao webhook do orquestrador na entrega proativa ({@code
 * #/$defs/LoteEntrega}).
 */
public record DeliveryBatchDto(
        String versao,
        String loteId,
        String origem,
        String criadoEm,
        int tentativa,
        List<JsonNode> acoes) {}
