package br.com.felipeblaft.reports.domain.job;

/** Código estável do resultado de um trabalho, entregue aos dois canais (FR-021). */
public enum JobResult {
    READY_FOR_REVIEW(true),
    PENDING_QUESTIONS(true),
    NEW_VERSION(true),
    NO_CONTENT_CHANGE(true),
    ACTION_REQUEST_IGNORED(true),
    STALE_VERSION(true),
    AUDIO_NOT_PROCESSABLE(false),
    STRUCTURING_FAILED(false),
    CHANGE_FAILED(false),
    ANSWER_FAILED(false),
    CONSOLIDATION_FAILED(false),
    DISCARDED(false);

    private final boolean success;

    JobResult(boolean success) {
        this.success = success;
    }

    /** O trabalho terminou normalmente (mesmo sem nova versão). */
    public boolean success() {
        return success;
    }
}
