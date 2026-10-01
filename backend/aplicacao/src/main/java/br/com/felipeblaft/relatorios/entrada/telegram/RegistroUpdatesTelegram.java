package br.com.felipeblaft.relatorios.entrada.telegram;

import java.time.Instant;

/** Idempotência do webhook: um {@code update_id} repetido é confirmado sem reprocessar. */
public interface RegistroUpdatesTelegram {

    /** Registra o update; devolve {@code false} se ele já tinha sido processado. */
    boolean registrarSeNovo(long updateId, Instant agora);

    int removerAntigos(Instant limite);
}
