package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Corpo 200 de {@code POST /atualizacao} ({@code #/$defs/RespostaAtualizacao}). As ações já vêm
 * montadas (com {@code acaoId}) na ordem de execução; lista vazia = nada a executar.
 */
public record AtualizacaoResposta(
        String versao, Long updateId, String loteId, List<JsonNode> acoes) {

    public static final String VERSAO = "v1";

    public static AtualizacaoResposta vazia() {
        return new AtualizacaoResposta(VERSAO, null, null, List.of());
    }

    public static AtualizacaoResposta de(long updateId, List<JsonNode> acoes) {
        return new AtualizacaoResposta(VERSAO, updateId, "u" + updateId, acoes);
    }
}
