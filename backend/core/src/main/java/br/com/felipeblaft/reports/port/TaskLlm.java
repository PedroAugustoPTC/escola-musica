package br.com.felipeblaft.reports.port;

/** Tarefas pedidas ao modelo de linguagem; cada uma tem prompt versionado e schema de saída. */
public enum TaskLlm {
    ESTRUTURACAO_AULA,
    INCORPORACAO_RESPOSTAS,
    REVISAO_AULA,
    CONSOLIDACAO_SEMESTRAL,
    REVISAO_SEMESTRAL
}
