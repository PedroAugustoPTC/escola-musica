package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Outbox de ações de canal (data-model.md §1.2). */
public interface ChannelActionRepository {

    /**
     * Pendências para as métricas: ações {@code PENDENTE} (e a criação da mais antiga) e ações
     * {@code ENTREGUE} que seguem sem confirmação além do limite informado.
     */
    record Pending(long quantity, Instant oldestCreatedAt, long withoutConfirmation) {}

    /** Resultado do expurgo: conteúdos zerados e registros removidos. */
    record Purge(int resetPayloads, int removedRegistrations) {}

    /**
     * Grava as ações que ainda não existem (idempotente por {@code id}, nunca sobrescreve); devolve
     * quantas foram de fato gravadas.
     */
    int saveBatch(List<ChannelAction> actions);

    Optional<ChannelAction> searchById(String id);

    /** Ações do update, na ordem de criação ({@code seq}). */
    List<ChannelAction> searchByUpdate(long updateId);

    /** Ações do lote, na ordem de criação ({@code seq}). */
    List<ChannelAction> searchByBatch(String batchId);

    /**
     * Reserva o próximo lote devido, sem disputar com outros despachantes: a reserva empurra a
     * próxima tentativa para {@code leaseAte}, de modo que um despachante que morre deixa o lote
     * reentregável quando o lease vence. Respeita a ordem por chat: ignora o chat que ainda tem
     * ação anterior de outro lote {@code PENDENTE} ou {@code ENTREGUE} há menos de {@code
     * confirmacaoMax}. Devolve o lote inteiro, na ordem de criação, ou lista vazia.
     */
    List<ChannelAction> reserveNextDueBatch(
            Instant now, Duration confirmationMax, Instant leaseAte);

    /**
     * Grava a ação só se a versão armazenada ainda for {@code versaoEsperada} (controle otimista);
     * devolve {@code false} se outro ator gravou antes. Em caso de sucesso a versão da ação avança.
     */
    boolean updateIfVersion(ChannelAction action, int expectedVersion);

    /**
     * Encerra, com o código {@code SEM_CONFIRMACAO}, as ações {@code ENTREGUE} que o orquestrador
     * nunca confirmou e cujo prazo já venceu (FR-029); devolve quantas foram encerradas.
     */
    int expireOverdueDelivered(Instant now);

    /**
     * Zera o conteúdo das ações finais anteriores a {@code payloadAte} e remove os registros finais
     * anteriores a {@code registroAte}.
     */
    Purge purge(Instant payloadAte, Instant registrationUntil);

    /**
     * @param entreguesAte entregues antes deste instante e ainda sem confirmação contam como
     *     atrasadas
     */
    Pending pending(Instant deliveredUntil);
}
