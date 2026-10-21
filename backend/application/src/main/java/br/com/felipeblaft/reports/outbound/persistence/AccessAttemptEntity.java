package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_attempt")
public class AccessAttemptEntity {

    @Id private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(name = "source_hash", nullable = false)
    private String sourceHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "window_started_at")
    private Instant windowStartedAt;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccessAttemptEntity() {}

    AccessAttemptEntity(AccessAttempt attempt) {
        this.id = attempt.id();
        this.scope = attempt.scope();
        this.channel = attempt.channel();
        this.sourceHash = attempt.originHash();
        copyStateOf(attempt);
    }

    /** Copia para a linha o estado que a regra do domínio acabou de calcular. */
    void copyStateOf(AccessAttempt attempt) {
        this.failedAttempts = attempt.failedAttempts();
        this.windowStartedAt = attempt.windowStartedAt();
        this.blockedUntil = attempt.blockedUntil();
        this.updatedAt = attempt.updatedAt();
    }

    AccessAttempt toDomain() {
        return new AccessAttempt(
                id,
                scope,
                channel,
                sourceHash,
                failedAttempts,
                windowStartedAt,
                blockedUntil,
                updatedAt);
    }
}
