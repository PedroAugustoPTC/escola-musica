package br.com.felipeblaft.reports.domain.channel;

/** Estados da ação na outbox (data-model.md §1.2.2). */
public enum ChannelActionState {
    PENDENTE,
    ENTREGUE,
    EXECUTADA,
    FALHOU,
    FALHA_DEFINITIVA,
    DESCARTADA;

    /** Estado final nunca volta. */
    public boolean finalized() {
        return this != PENDENTE && this != ENTREGUE;
    }
}
