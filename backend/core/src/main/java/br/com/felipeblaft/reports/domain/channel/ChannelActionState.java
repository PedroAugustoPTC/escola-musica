package br.com.felipeblaft.reports.domain.channel;

/** Estados da ação na outbox (data-model.md §1.2.2). */
public enum ChannelActionState {
    PENDING,
    DELIVERED,
    EXECUTED,
    FAILED,
    PERMANENTLY_FAILED,
    DISCARDED;

    /** Estado final nunca volta. */
    public boolean finalized() {
        return this != PENDING && this != DELIVERED;
    }
}
