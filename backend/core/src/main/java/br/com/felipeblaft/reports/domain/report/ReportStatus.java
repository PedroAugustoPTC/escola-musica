package br.com.felipeblaft.reports.domain.report;

/** Estados do relatório (data-model.md §8.1). */
public enum ReportStatus {
    EM_PROCESSAMENTO,
    FALHA_PROCESSAMENTO,
    AGUARDANDO_RESPOSTAS,
    AGUARDANDO_CONFERENCIA,
    APROVADO,
    CANCELADO;

    /** Em aberto: iniciado e ainda não aprovado nem cancelado (FR-034). */
    public boolean isOpen() {
        return this != APROVADO && this != CANCELADO;
    }
}
