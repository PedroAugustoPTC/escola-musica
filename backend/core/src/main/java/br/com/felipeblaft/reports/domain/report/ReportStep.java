package br.com.felipeblaft.reports.domain.report;

/** Etapa de processamento em curso no relatório; nula quando não há trabalho (data-model.md §8). */
public enum ReportStep {
    ESTRUTURANDO,
    INCORPORANDO_RESPOSTAS,
    TRANSCREVENDO_PEDIDO,
    APLICANDO_ALTERACAO,
    GERANDO_PDF
}
