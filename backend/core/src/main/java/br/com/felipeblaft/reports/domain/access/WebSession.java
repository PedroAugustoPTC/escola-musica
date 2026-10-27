package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessão da Web (data-model.md §4): expiração deslizante por inatividade (FR-008), renovada a cada
 * uso com no máximo uma escrita por minuto. No banco fica só o hash do token.
 */
public class WebSession {

    private final UUID id;
    private final UUID teacherId;
    private final String tokenHash;
    private final Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private SessionRevocationReason revocationReason;

    public WebSession(
            UUID id,
            UUID teacherId,
            String tokenHash,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt) {
        this(id, teacherId, tokenHash, createdAt, lastUsedAt, expiresAt, null, null);
    }

    /**
     * Reconstrói a sessão com o estado de revogação (R-08): a data e o motivo vêm juntos ou não
     * vêm.
     */
    public WebSession(
            UUID id,
            UUID teacherId,
            String tokenHash,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            Instant revokedAt,
            SessionRevocationReason revocationReason) {
        if ((revokedAt == null) != (revocationReason == null)) {
            throw new IllegalArgumentException("a revogação exige data e motivo juntos");
        }
        this.id = Objects.requireNonNull(id);
        this.teacherId = Objects.requireNonNull(teacherId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.lastUsedAt = Objects.requireNonNull(lastUsedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.revokedAt = revokedAt;
        this.revocationReason = revocationReason;
    }

    public static WebSession create(
            UUID teacherId, String tokenHash, Instant now, Duration inactivity) {
        return new WebSession(
                GeneratorId.create(), teacherId, tokenHash, now, now, now.plus(inactivity));
    }

    /** Válida = não revogada e dentro da expiração deslizante. */
    public boolean validAt(Instant instant) {
        return revokedAt == null && instant.isBefore(expiresAt);
    }

    /**
     * Marca a sessão como revogada, sem removê-la (R-08). A primeira revogação prevalece; devolve
     * {@code true} se esta chamada a marcou.
     */
    public boolean revoke(SessionRevocationReason reason, Instant now) {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);
        if (revokedAt != null) {
            return false;
        }
        revokedAt = now;
        revocationReason = reason;
        return true;
    }

    public boolean revoked() {
        return revokedAt != null;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public SessionRevocationReason revocationReason() {
        return revocationReason;
    }

    /**
     * Renova a expiração; devolve {@code true} se precisa gravar (1 escrita por minuto, no máximo).
     */
    public boolean registerUse(Instant now, Duration inactivity) {
        if (Duration.between(lastUsedAt, now).compareTo(BusinessParameters.SESSION_RENEWAL_INTERVAL)
                < 0) {
            return false;
        }
        lastUsedAt = now;
        expiresAt = now.plus(inactivity);
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID teacherId() {
        return teacherId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
