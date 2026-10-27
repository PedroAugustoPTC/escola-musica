package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox no PostgreSQL (R-04 a R-06): gravação idempotente por id, reserva do lote devido com
 * {@code FOR UPDATE SKIP LOCKED} e lease, e atualização condicional pela coluna {@code versao}. As
 * leituras são destacadas do contexto de persistência para que uma releitura na mesma transação
 * (ex.: nova tentativa de uma confirmação) sempre veja o que está no banco.
 */
@Repository
class ChannelActionRepositoryAdapter implements ChannelActionRepository {

    private final ChannelActionJpa jpa;
    private final EntityManager manager;

    ChannelActionRepositoryAdapter(ChannelActionJpa jpa, EntityManager manager) {
        this.jpa = jpa;
        this.manager = manager;
    }

    @Override
    @Transactional
    public int saveBatch(List<ChannelAction> actions) {
        int saved = 0;
        for (ChannelAction a : actions) {
            saved +=
                    jpa.insertIfNew(
                            a.id(),
                            a.batchId(),
                            a.channel().name(),
                            a.origin().name(),
                            a.updateId(),
                            a.chatId(),
                            a.teacherId(),
                            a.jobId(),
                            a.type().name(),
                            a.payload(),
                            a.compensation(),
                            a.state().name(),
                            a.attempts(),
                            a.nextAttemptAt(),
                            a.deadlineAt(),
                            a.version(),
                            a.createdAt(),
                            a.deliveredAt(),
                            a.finalizedAt(),
                            a.reasonCode());
        }
        return saved;
    }

    @Override
    @Transactional
    public Optional<ChannelAction> searchById(String id) {
        return jpa.findById(id).map(this::read);
    }

    @Override
    @Transactional
    public List<ChannelAction> searchByUpdate(long updateId) {
        return jpa.findByUpdateIdOrderBySeq(updateId).stream().map(this::read).toList();
    }

    @Override
    @Transactional
    public List<ChannelAction> searchByBatch(String batchId) {
        return jpa.findByBatchIdOrderBySeq(batchId).stream().map(this::read).toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ChannelAction> reserveNextDueBatch(
            Instant now, Duration confirmationMax, Instant leaseUntil) {
        Optional<String> batch = jpa.nextDueBatch(now, now.minus(confirmationMax));
        if (batch.isEmpty()) {
            return List.of();
        }
        jpa.reserveBatch(batch.get(), leaseUntil);
        return jpa.findByBatchIdOrderBySeq(batch.get()).stream()
                .filter(e -> ChannelActionState.PENDING.name().equals(e.state))
                .map(this::read)
                .toList();
    }

    @Override
    @Transactional
    public boolean updateIfVersion(ChannelAction action, int expectedVersion) {
        int rows =
                jpa.updateIfVersion(
                        action.id(),
                        expectedVersion,
                        action.state().name(),
                        action.attempts(),
                        action.nextAttemptAt(),
                        action.deliveredAt(),
                        action.finalizedAt(),
                        action.reasonCode(),
                        action.payload(),
                        action.compensation());
        if (rows == 0) {
            return false;
        }
        action.defineVersion(expectedVersion + 1);
        return true;
    }

    @Override
    @Transactional
    public int expireOverdueDelivered(Instant now) {
        return jpa.expireOverdueDelivered(now);
    }

    @Override
    @Transactional
    public Purge purge(Instant payloadUntil, Instant registrationUntil) {
        int reset = jpa.clearFinalizedContentUntil(payloadUntil);
        int removed = jpa.removeFinalizedUntil(registrationUntil);
        return new Purge(reset, removed);
    }

    @Override
    @Transactional
    public Pending pending(Instant deliveredUntil) {
        return new Pending(
                jpa.countPendings(),
                jpa.oldestPendingCreated().orElse(null),
                jpa.countDeliveredSince(deliveredUntil));
    }

    @Override
    @Transactional
    public int discardPendingByChat(long chatId, Instant now) {
        return jpa.discardPendingByChat(chatId, now, ChannelAction.LINK_UNDONE_CODE);
    }

    private ChannelAction read(ChannelActionEntity e) {
        ChannelAction action =
                new ChannelAction(
                        e.id,
                        e.batchId,
                        Channel.valueOf(e.channel),
                        ChannelActionOrigin.valueOf(e.origin),
                        e.updateId,
                        e.chatId,
                        e.teacherId,
                        e.jobId,
                        ChannelActionType.valueOf(e.type),
                        e.payload,
                        e.compensation,
                        ChannelActionState.valueOf(e.state),
                        e.attempts,
                        e.nextAttemptAt,
                        e.deadlineAt,
                        e.createdAt,
                        e.deliveredAt,
                        e.finalizedAt,
                        e.reasonCode,
                        e.version);
        manager.detach(e);
        return action;
    }
}
