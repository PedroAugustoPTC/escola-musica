package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Outbox em memória: reproduz a ordem por {@code seq}, a idempotência por id, o portão de ordem por
 * chat e a atualização condicional por {@code versao}. Guarda e devolve cópias, como um banco.
 */
public class InMemoryChannelActionRepository implements ChannelActionRepository {

    private final Map<String, ChannelAction> data = new LinkedHashMap<>();
    private final Map<String, Long> sequences = new LinkedHashMap<>();
    private long nextSequence = 1;

    /** Chamado logo antes de cada {@code atualizarSeVersao}: simula um ator concorrente. */
    public Runnable beforeUpdate = () -> {};

    @Override
    public int saveBatch(List<ChannelAction> actions) {
        int saved = 0;
        for (ChannelAction action : actions) {
            if (!data.containsKey(action.id())) {
                data.put(action.id(), copy(action));
                sequences.put(action.id(), nextSequence++);
                saved++;
            }
        }
        return saved;
    }

    @Override
    public Optional<ChannelAction> searchById(String id) {
        return Optional.ofNullable(data.get(id)).map(InMemoryChannelActionRepository::copy);
    }

    @Override
    public List<ChannelAction> searchByUpdate(long updateId) {
        return ordered(a -> a.updateId() != null && a.updateId() == updateId);
    }

    @Override
    public List<ChannelAction> searchByBatch(String batchId) {
        return ordered(a -> a.batchId().equals(batchId));
    }

    @Override
    public List<ChannelAction> reserveNextDueBatch(
            Instant now, Duration confirmationMax, Instant leaseAte) {
        Instant limit = now.minus(confirmationMax);
        Optional<ChannelAction> candidate =
                data.values().stream()
                        .filter(a -> a.state() == ChannelActionState.PENDING)
                        .filter(a -> !a.nextAttemptAt().isAfter(now))
                        .filter(this::firstPendingOfBatch)
                        .filter(a -> !blockedByOrder(a, limit))
                        .min(Comparator.comparingLong(a -> sequences.get(a.id())));
        if (candidate.isEmpty()) {
            return List.of();
        }
        String batch = candidate.get().batchId();
        List<ChannelAction> reserved = new ArrayList<>();
        for (ChannelAction action : ordered(a -> a.batchId().equals(batch))) {
            ChannelAction stored = data.get(action.id());
            if (stored.state() == ChannelActionState.PENDING) {
                stored.reserveUntil(leaseAte);
                stored.defineVersion(stored.version() + 1);
                reserved.add(copy(stored));
            }
        }
        return reserved;
    }

    @Override
    public boolean updateIfVersion(ChannelAction action, int expectedVersion) {
        beforeUpdate.run();
        ChannelAction current = data.get(action.id());
        if (current == null || current.version() != expectedVersion) {
            return false;
        }
        ChannelAction saved = copy(action);
        saved.defineVersion(expectedVersion + 1);
        data.put(action.id(), saved);
        action.defineVersion(expectedVersion + 1);
        return true;
    }

    @Override
    public int expireOverdueDelivered(Instant now) {
        int expired = 0;
        for (ChannelAction action : data.values()) {
            if (action.expireWithoutConfirmation(now)) {
                action.defineVersion(action.version() + 1);
                expired++;
            }
        }
        return expired;
    }

    @Override
    public Purge purge(Instant payloadAte, Instant registrationUntil) {
        int reset = 0;
        for (ChannelAction action : data.values()) {
            if (action.state().finalized()
                    && action.finalizedAt().isBefore(payloadAte)
                    && (action.payload() != null || action.compensation() != null)) {
                action.resetPayload();
                reset++;
            }
        }
        int before = data.size();
        data.values()
                .removeIf(
                        a -> a.state().finalized() && a.finalizedAt().isBefore(registrationUntil));
        return new Purge(reset, before - data.size());
    }

    @Override
    public Pending pending(Instant deliveredUntil) {
        long withoutConfirmation =
                data.values().stream()
                        .filter(a -> a.state() == ChannelActionState.DELIVERED)
                        .filter(a -> a.deliveredAt().isBefore(deliveredUntil))
                        .count();
        List<ChannelAction> pending =
                data.values().stream()
                        .filter(a -> a.state() == ChannelActionState.PENDING)
                        .toList();
        Instant oldest =
                pending.stream()
                        .map(ChannelAction::createdAt)
                        .min(Comparator.naturalOrder())
                        .orElse(null);
        return new Pending(pending.size(), oldest, withoutConfirmation);
    }

    /** Estado guardado (não uma cópia) para asserções. */
    public ChannelAction stored(String id) {
        return data.get(id);
    }

    public List<ChannelAction> all() {
        return ordered(a -> true);
    }

    private List<ChannelAction> ordered(Predicate<ChannelAction> filter) {
        return data.values().stream()
                .filter(filter)
                .sorted(Comparator.comparingLong(a -> sequences.get(a.id())))
                .map(InMemoryChannelActionRepository::copy)
                .toList();
    }

    private boolean firstPendingOfBatch(ChannelAction action) {
        long minimum =
                data.values().stream()
                        .filter(a -> a.batchId().equals(action.batchId()))
                        .filter(a -> a.state() == ChannelActionState.PENDING)
                        .mapToLong(a -> sequences.get(a.id()))
                        .min()
                        .orElseThrow();
        return sequences.get(action.id()) == minimum;
    }

    private boolean blockedByOrder(ChannelAction action, Instant confirmationLimit) {
        long seq = sequences.get(action.id());
        return data.values().stream()
                .filter(b -> b.chatId() == action.chatId())
                .filter(b -> sequences.get(b.id()) < seq)
                .filter(b -> !b.batchId().equals(action.batchId()))
                .anyMatch(
                        b ->
                                b.state() == ChannelActionState.PENDING
                                        || (b.state() == ChannelActionState.DELIVERED
                                                && b.deliveredAt().isAfter(confirmationLimit)));
    }

    private static ChannelAction copy(ChannelAction a) {
        return new ChannelAction(
                a.id(),
                a.batchId(),
                a.channel(),
                a.origin(),
                a.updateId(),
                a.chatId(),
                a.teacherId(),
                a.jobId(),
                a.type(),
                a.payload(),
                a.compensation(),
                a.state(),
                a.attempts(),
                a.nextAttemptAt(),
                a.deadlineAt(),
                a.createdAt(),
                a.deliveredAt(),
                a.finalizedAt(),
                a.reasonCode(),
                a.version());
    }
}
