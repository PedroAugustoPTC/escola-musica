package br.com.felipeblaft.relatorios.dominio.trabalho;

public enum StatusTrabalho {
    PENDENTE,
    EM_EXECUCAO,
    CONCLUIDO,
    FALHOU,
    DESCARTADO;

    public boolean emCurso() {
        return this == PENDENTE || this == EM_EXECUCAO;
    }
}
