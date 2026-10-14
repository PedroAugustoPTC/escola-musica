package br.com.felipeblaft.reports.domain.job;

public enum JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    DISCARDED;

    public boolean inProgress() {
        return this == PENDING || this == RUNNING;
    }
}
