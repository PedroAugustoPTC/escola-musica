package br.com.felipeblaft.relatorios.dominio.canal;

/** Efeito de uma confirmação do orquestrador sobre a ação (invariante 2 de data-model.md). */
public enum ResultadoConfirmacao {
    /** A ação mudou de estado por causa da confirmação. */
    APLICADA,
    /** Confirmação idêntica à já registrada: inócua. */
    REPETIDA,
    /** A ação já está em outro estado final: a confirmação é ignorada e contada. */
    DIVERGENTE
}
