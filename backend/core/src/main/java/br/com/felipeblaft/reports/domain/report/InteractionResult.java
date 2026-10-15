package br.com.felipeblaft.reports.domain.report;

public enum InteractionResult {
    APPLIED,
    NO_CONTENT_CHANGE,
    ACTION_REQUEST_IGNORED,
    FAILED,
    REJECTED_STALE_VERSION
}
