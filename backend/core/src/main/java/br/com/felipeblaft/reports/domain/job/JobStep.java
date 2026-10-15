package br.com.felipeblaft.reports.domain.job;

/** Etapas de processamento mostradas nos dois canais (FR-021, R-06). */
public enum JobStep {
    UPLOADING,
    TRANSCRIBING,
    STRUCTURING,
    INCORPORATING_ANSWERS,
    TRANSCRIBING_REQUEST,
    APPLYING_CHANGE,
    CONSOLIDATING,
    GENERATING_PDF,
    READY
}
