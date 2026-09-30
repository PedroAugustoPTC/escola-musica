package br.com.felipeblaft.relatorios.dominio.relatorio;

/** Estados do relatório (data-model.md §8.1). */
public enum StatusRelatorio {
    EM_PROCESSAMENTO,
    FALHA_PROCESSAMENTO,
    AGUARDANDO_RESPOSTAS,
    AGUARDANDO_CONFERENCIA,
    APROVADO,
    CANCELADO;

    /** Em aberto: iniciado e ainda não aprovado nem cancelado (FR-034). */
    public boolean emAberto() {
        return this != APROVADO && this != CANCELADO;
    }
}
