package br.com.felipeblaft.reports.domain.privacy;

/**
 * Tipo de item tratado numa execução da retenção; espelha o CHECK de {@code retention_run_item}.
 */
public enum RetentionItemType {
    LESSON_SOURCE,
    SEMESTER_SOURCE,
    UNAPPROVED_PDF,
    CANCELLED_LESSON_REPORT,
    CANCELLED_SEMESTER_REPORT,
    PDF_FILE,
    AUDIT_EVENTS,
    ALERT_HISTORY,
    RUN_HISTORY
}
