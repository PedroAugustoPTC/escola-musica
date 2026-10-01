package br.com.felipeblaft.relatorios.porta;

/**
 * Modelo de linguagem (contracts/portas-provedores.md §3). O adaptador escolhe o prompt versionado,
 * pede JSON, valida contra o schema e faz uma chamada de correção; se ainda for inválido lança
 * {@link SaidaIaInvalidaExcecao}. Indisponibilidade lança {@link ProvedorIndisponivelExcecao}.
 */
public interface LlmPorta {

    <T> RespostaLlm<T> gerarJson(TarefaLlm tarefa, EntradaTarefa entrada, Class<T> tipoSaida);
}
