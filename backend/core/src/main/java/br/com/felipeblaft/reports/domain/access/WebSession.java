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

    public WebSession(
            UUID id,
            UUID teacherId,
            String tokenHash,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id);
        this.teacherId = Objects.requireNonNull(teacherId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.lastUsedAt = Objects.requireNonNull(lastUsedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public static WebSession create(
            UUID teacherId, String tokenHash, Instant now, Duration inactivity) {
        return new WebSession(
                GeneratorId.create(), teacherId, tokenHash, now, now, now.plus(inactivity));
    }

    public boolean validAt(Instant instant) {
        return instant.isBefore(expiresAt);
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
