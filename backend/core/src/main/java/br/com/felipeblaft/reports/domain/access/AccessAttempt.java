package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tentativas incorretas de código por origem (data-model.md §6, FR-007): após o máximo de falhas
 * consecutivas, bloqueia por um período; um sucesso ou o fim do bloqueio zeram a contagem.
 */
public class AccessAttempt {

    private final UUID id;
    private final Channel channel;
    private final String originHash;
    private int failedAttempts;
    private Instant blockedUntil;
    private Instant updatedAt;

    public AccessAttempt(
            UUID id,
            Channel channel,
            String originHash,
            int failedAttempts,
            Instant blockedUntil,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.channel = Objects.requireNonNull(channel);
        this.originHash = Objects.requireNonNull(originHash);
        this.failedAttempts = failedAttempts;
        this.blockedUntil = blockedUntil;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static AccessAttempt create(Channel channel, String originHash, Instant now) {
        return new AccessAttempt(GeneratorId.create(), channel, originHash, 0, null, now);
    }

    /** Tempo restante de bloqueio, se houver. */
    public Optional<Duration> remainingLock(Instant now) {
        if (blockedUntil != null && now.isBefore(blockedUntil)) {
            return Optional.of(Duration.between(now, blockedUntil));
        }
        return Optional.empty();
    }

    /** Bloqueio que já terminou: a contagem deve ser zerada (lição 5 do MVP). */
    public boolean lockEnded(Instant now) {
        return blockedUntil != null && !now.isBefore(blockedUntil);
    }

    public void registerFailure(Instant now, int maxAttempts, Duration lockDuration) {
        if (lockEnded(now)) {
            reset(now);
        }
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            blockedUntil = now.plus(lockDuration);
        }
        updatedAt = now;
    }

    public void reset(Instant now) {
        failedAttempts = 0;
        blockedUntil = null;
        updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public Channel channel() {
        return channel;
    }

    public String originHash() {
        return originHash;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant blockedUntil() {
        return blockedUntil;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
