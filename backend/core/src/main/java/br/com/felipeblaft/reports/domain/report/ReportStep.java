package br.com.felipeblaft.reports.domain.report;

/** Etapa de processamento em curso no relatório; nula quando não há trabalho (data-model.md §8). */
public enum ReportStep {
    STRUCTURING,
    INCORPORATING_ANSWERS,
    TRANSCRIBING_REQUEST,
    APPLYING_CHANGE,
    GENERATING_PDF
}
