package br.com.felipeblaft.reports.domain.job;

public enum JobStatus {
    PENDENTE,
    EM_EXECUCAO,
    CONCLUIDO,
    FALHOU,
    DESCARTADO;

    public boolean inProgress() {
        return this == PENDENTE || this == EM_EXECUCAO;
    }
}
