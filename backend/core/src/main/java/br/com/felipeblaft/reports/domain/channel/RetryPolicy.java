package br.com.felipeblaft.reports.domain.channel;

import java.time.Duration;
import java.time.Instant;
import java.util.function.DoubleSupplier;

/**
 * Política de retentativa da entrega ao orquestrador: espera exponencial a partir do intervalo
 * inicial, limitada pelo teto, com jitter injetável e prazo total (data-model.md §1.2.2).
 */
public final class RetryPolicy {

    /** Jitter de ±10% (contracts/canal-telegram-interno.md §3). */
    private static final double MAX_JITTER = 0.1;

    private final Duration initial;
    private final Duration ceiling;
    private final Duration deadline;
    private final DoubleSupplier jitter;

    /**
     * @param jitter devolve um fator em [-1, 1] aplicado como {@code espera × (1 + fator × 0,1)};
     *     zero nos testes determinísticos
     */
    public RetryPolicy(
            Duration initial, Duration ceiling, Duration deadline, DoubleSupplier jitter) {
        if (initial == null || initial.isZero() || initial.isNegative()) {
            throw new IllegalArgumentException("intervalo inicial deve ser positivo");
        }
        if (ceiling == null || ceiling.compareTo(initial) < 0) {
            throw new IllegalArgumentException("teto deve ser maior ou igual ao intervalo inicial");
        }
        if (deadline == null || deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException("prazo deve ser positivo");
        }
        this.initial = initial;
        this.ceiling = ceiling;
        this.deadline = deadline;
        this.jitter = jitter == null ? () -> 0.0 : jitter;
    }

    public RetryPolicy(Duration initial, Duration ceiling, Duration deadline) {
        this(initial, ceiling, deadline, () -> 0.0);
    }

    /** Próxima tentativa depois da n-ésima falha (n ≥ 1). */
    public Instant nextAttempt(int attempts, Instant now) {
        if (attempts < 1) {
            throw new IllegalArgumentException("tentativas deve ser maior ou igual a 1");
        }
        double seconds = initial.toMillis() / 1000.0;
        double limit = ceiling.toMillis() / 1000.0;
        for (int i = 1; i < attempts && seconds < limit; i++) {
            seconds *= 2;
        }
        seconds = Math.min(seconds, limit);
        double factor = Math.max(-1.0, Math.min(1.0, jitter.getAsDouble()));
        long milliseconds = Math.round(seconds * 1000.0 * (1.0 + factor * MAX_JITTER));
        return now.plusMillis(Math.max(milliseconds, 0));
    }

    /**
     * Próxima tentativa nunca depois do prazo: a falha definitiva é registrada no prazo, não até o
     * teto de espera depois dele.
     */
    public Instant nextAttempt(int attempts, Instant now, Instant deadlineAt) {
        Instant next = nextAttempt(attempts, now);
        return next.isAfter(deadlineAt) ? deadlineAt : next;
    }

    public Instant deadlineOf(Instant createdAt) {
        return createdAt.plus(deadline);
    }

    public Duration deadline() {
        return deadline;
    }
}
