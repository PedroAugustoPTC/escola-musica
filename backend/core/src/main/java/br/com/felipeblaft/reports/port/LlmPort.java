package br.com.felipeblaft.reports.port;

/**
 * Modelo de linguagem (contracts/portas-provedores.md §3). O adaptador escolhe o prompt versionado,
 * pede JSON, valida contra o schema e faz uma chamada de correção; se ainda for inválido lança
 * {@link InvalidAiOutputException}. Indisponibilidade lança {@link ProviderUnavailableException}.
 */
public interface LlmPort {

    <T> ResponseLlm<T> generateJson(TaskLlm task, TaskInput input, Class<T> outputType);
}
