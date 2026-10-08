package br.com.felipeblaft.relatorios.entrada.telegram;

import java.time.Instant;
import java.util.Optional;

/**
 * Idempotência da entrada do Telegram: um {@code update_id} repetido não é reprocessado. Serve ao
 * webhook antigo (que só registra) e à rota interna do canal (que também guarda quantas ações o
 * update produziu, para reentregá-las).
 */
public interface RegistroUpdatesTelegram {

    /** Origem gravada para updates que entraram pela rota interna do canal. */
    String ORIGEM_INTERNO = "INTERNO";

    /**
     * Situação de um update já registrado.
     *
     * @param totalAcoes nulo enquanto o update ainda está sendo processado (ou se a linha se
     *     perdeu)
     * @param interno {@code false} nas linhas do webhook antigo
     */
    record SituacaoUpdate(Integer totalAcoes, Instant recebidoEm, boolean interno) {}

    /**
     * Registra o update (webhook antigo); devolve {@code false} se ele já tinha sido processado.
     */
    boolean registrarSeNovo(long updateId, Instant agora);

    /** Registra o update com a origem informada; devolve {@code false} se ele já existia. */
    boolean registrarSeNovo(long updateId, Instant agora, String origem);

    /** Grava quantas ações o update produziu: ele deixa de estar "em processamento". */
    void concluir(long updateId, int totalAcoes);

    Optional<SituacaoUpdate> situacao(long updateId);

    int removerAntigos(Instant limite);
}
