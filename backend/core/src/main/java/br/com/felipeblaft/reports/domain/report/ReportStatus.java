package br.com.felipeblaft.reports.domain.report;

/** Estados do relatório (data-model.md §8.1). */
public enum ReportStatus {
    PROCESSING,
    PROCESSING_FAILED,
    AWAITING_ANSWERS,
    AWAITING_REVIEW,
    APPROVED,
    CANCELLED;

    /** Em aberto: iniciado e ainda não aprovado nem cancelado (FR-034). */
    public boolean isOpen() {
        return this != APPROVED && this != CANCELLED;
    }
}
