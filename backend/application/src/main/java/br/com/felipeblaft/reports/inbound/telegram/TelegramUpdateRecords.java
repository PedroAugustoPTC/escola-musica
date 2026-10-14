package br.com.felipeblaft.reports.inbound.telegram;

import java.time.Instant;
import java.util.Optional;

/**
 * Idempotência da entrada do Telegram: um {@code update_id} repetido não é reprocessado. Serve ao
 * webhook antigo (que só registra) e à rota interna do canal (que também guarda quantas ações o
 * update produziu, para reentregá-las).
 */
public interface TelegramUpdateRecords {

    /** Origem gravada para updates que entraram pela rota interna do canal. */
    String INTERNAL_ORIGIN = "INTERNO";

    /**
     * Situação de um update já registrado.
     *
     * @param totalAcoes nulo enquanto o update ainda está sendo processado (ou se a linha se
     *     perdeu)
     * @param interno {@code false} nas linhas do webhook antigo
     */
    record UpdateStatus(Integer totalActions, Instant receivedAt, boolean internal) {}

    /**
     * Registra o update (webhook antigo); devolve {@code false} se ele já tinha sido processado.
     */
    boolean registerIfNew(long updateId, Instant now);

    /** Registra o update com a origem informada; devolve {@code false} se ele já existia. */
    boolean registerIfNew(long updateId, Instant now, String origin);

    /** Grava quantas ações o update produziu: ele deixa de estar "em processamento". */
    void complete(long updateId, int totalActions);

    Optional<UpdateStatus> status(long updateId);

    int removeOld(Instant limit);
}
