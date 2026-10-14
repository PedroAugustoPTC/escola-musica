package br.com.felipeblaft.reports.inbound.telegram.channel;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Corpo 200 de {@code POST /atualizacao} ({@code #/$defs/RespostaAtualizacao}). As ações já vêm
 * montadas (com {@code acaoId}) na ordem de execução; lista vazia = nada a executar.
 */
public record UpdateResponse(
        String version, Long updateId, String batchId, List<JsonNode> actions) {

    public static final String VERSION = "v1";

    public static UpdateResponse empty() {
        return new UpdateResponse(VERSION, null, null, List.of());
    }

    public static UpdateResponse of(long updateId, List<JsonNode> actions) {
        return new UpdateResponse(VERSION, updateId, "u" + updateId, actions);
    }
}
