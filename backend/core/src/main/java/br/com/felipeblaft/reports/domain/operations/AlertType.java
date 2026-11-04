package br.com.felipeblaft.reports.domain.operations;

/** Tipos de alerta operacional (FR-055). Cada alerta ativo é único por tipo e componente. */
public enum AlertType {
    AI_PROVIDER_FAILING,
    CHANNEL_QUEUE_BACKLOG,
    STORAGE_FAILED,
    RETENTION_FAILED,
    AUDIT_CHAIN_BROKEN
}
