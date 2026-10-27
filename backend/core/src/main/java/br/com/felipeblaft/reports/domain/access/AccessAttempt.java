package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tentativas incorretas por escopo, canal e origem (data-model.md §3.7, FR-006, FR-007, R-05): após
 * o máximo de falhas, bloqueia por um período. Falha durante um bloqueio ativo não conta nem
 * prorroga; um sucesso só zera a contagem quando não há bloqueio ativo; o fim do bloqueio zera a
 * contagem; com janela, a contagem recomeça quando a primeira falha ficou para trás.
 */
public class AccessAttempt {

    private final UUID id;
    private final AttemptScope scope;
    private final Channel channel;
    private final String originHash;
    private int failedAttempts;
    private Instant windowStartedAt;
    private Instant blockedUntil;
    private Instant updatedAt;

    public AccessAttempt(
            UUID id,
            AttemptScope scope,
            Channel channel,
            String originHash,
            int failedAttempts,
            Instant windowStartedAt,
            Instant blockedUntil,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.scope = Objects.requireNonNull(scope);
        this.channel = Objects.requireNonNull(channel);
        this.originHash = Objects.requireNonNull(originHash);
        this.failedAttempts = failedAttempts;
        this.windowStartedAt = windowStartedAt;
        this.blockedUntil = blockedUntil;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /** Registro novo do escopo informado, sem falhas. */
    public static AccessAttempt create(
            AttemptScope scope, Channel channel, String originHash, Instant now) {
        return new AccessAttempt(
                GeneratorId.create(), scope, channel, originHash, 0, null, null, now);
    }

    /** Registro novo do código de acesso do professor (comportamento da 001). */
    public static AccessAttempt create(Channel channel, String originHash, Instant now) {
        return create(AttemptScope.TEACHER_CODE, channel, originHash, now);
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

    /** Falhas consecutivas, sem janela. */
    public void registerFailure(Instant now, int maxAttempts, Duration lockDuration) {
        registerFailure(now, AttemptPolicy.consecutive(maxAttempts, lockDuration));
    }

    /**
     * Conta uma falha segundo a política do escopo. Durante um bloqueio ativo não conta nem
     * prorroga; no fim do bloqueio ou da janela, a contagem recomeça por esta falha.
     */
    public void registerFailure(Instant now, AttemptPolicy policy) {
        if (remainingLock(now).isPresent()) {
            return;
        }
        if (lockEnded(now) || windowPassed(now, policy)) {
            reset(now);
        }
        failedAttempts++;
        if (policy.hasWindow() && windowStartedAt == null) {
            windowStartedAt = now;
        }
        if (failedAttempts >= policy.maxAttempts()) {
            blockedUntil = now.plus(policy.lockDuration());
        }
        updatedAt = now;
    }

    /**
     * Confirma um sucesso: zera a contagem, exceto se houver bloqueio ativo (a senha correta que
     * chega depois da última falha continua recusada). Devolve se zerou.
     */
    public boolean confirmSuccess(Instant now) {
        if (remainingLock(now).isPresent()) {
            return false;
        }
        reset(now);
        return true;
    }

    public void reset(Instant now) {
        failedAttempts = 0;
        windowStartedAt = null;
        blockedUntil = null;
        updatedAt = now;
    }

    private boolean windowPassed(Instant now, AttemptPolicy policy) {
        return policy.hasWindow()
                && windowStartedAt != null
                && !now.isBefore(windowStartedAt.plus(policy.window()));
    }

    public UUID id() {
        return id;
    }

    public AttemptScope scope() {
        return scope;
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

    /** Início da janela de contagem; só existe nos escopos com janela e enquanto há falha nela. */
    public Instant windowStartedAt() {
        return windowStartedAt;
    }

    public Instant blockedUntil() {
        return blockedUntil;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
