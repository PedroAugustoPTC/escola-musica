package br.com.felipeblaft.reports.port;

/** Tarefas pedidas ao modelo de linguagem; cada uma tem prompt versionado e schema de saída. */
public enum TaskLlm {
    LESSON_STRUCTURING,
    ANSWER_INCORPORATION,
    LESSON_REVISION,
    SEMESTER_CONSOLIDATION,
    SEMESTER_REVISION
}
