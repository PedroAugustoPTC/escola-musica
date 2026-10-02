package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Corpo 200 de {@code POST /atualizacao} ({@code #/$defs/RespostaAtualizacao}). As ações já vêm
 * montadas (com {@code acaoId}) na ordem de execução; lista vazia = nada a executar.
 */
public record RespostaAtualizacao(String versao, Long updateId, String loteId, List<JsonNode> acoes) {

    public static final String VERSAO = "v1";

    public static RespostaAtualizacao vazia() {
        return new RespostaAtualizacao(VERSAO, null, null, List.of());
    }

    public static RespostaAtualizacao de(long updateId, List<JsonNode> acoes) {
        return new RespostaAtualizacao(VERSAO, updateId, "u" + updateId, acoes);
    }
}
