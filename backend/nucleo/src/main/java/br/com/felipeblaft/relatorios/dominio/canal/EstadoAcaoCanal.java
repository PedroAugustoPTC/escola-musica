package br.com.felipeblaft.relatorios.dominio.canal;

/** Estados da ação na outbox (data-model.md §1.2.2). */
public enum EstadoAcaoCanal {
    PENDENTE,
    ENTREGUE,
    EXECUTADA,
    FALHOU,
    FALHA_DEFINITIVA,
    DESCARTADA;

    /** Estado final nunca volta. */
    public boolean finalizado() {
        return this != PENDENTE && this != ENTREGUE;
    }
}
