package br.com.felipeblaft.reports.domain.channel;

/** Efeito de uma confirmação do orquestrador sobre a ação (invariante 2 de data-model.md). */
public enum ConfirmationResult {
    /** A ação mudou de estado por causa da confirmação. */
    APPLIED,
    /** Confirmação idêntica à já registrada: inócua. */
    REPEATED,
    /** A ação já está em outro estado final: a confirmação é ignorada e contada. */
    DIVERGENT
}
