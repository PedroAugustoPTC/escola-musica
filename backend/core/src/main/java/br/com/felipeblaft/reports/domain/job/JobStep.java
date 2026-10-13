package br.com.felipeblaft.reports.domain.job;

/** Etapas de processamento mostradas nos dois canais (FR-021, R-06). */
public enum JobStep {
    ENVIANDO,
    TRANSCREVENDO,
    ESTRUTURANDO,
    INCORPORANDO_RESPOSTAS,
    TRANSCREVENDO_PEDIDO,
    APLICANDO_ALTERACAO,
    CONSOLIDANDO,
    GERANDO_PDF,
    PRONTO
}
