package br.com.felipeblaft.reports.outbound.persistence;

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
    private Channel channel;

    @Column(name = "source_hash", nullable = false)
    private String sourceHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccessAttemptEntity() {}

    AccessAttemptEntity(
            UUID id,
            Channel channel,
            String sourceHash,
            int failedAttempts,
            Instant blockedUntil,
            Instant updatedAt) {
        this.id = id;
        this.channel = channel;
        this.sourceHash = sourceHash;
        this.failedAttempts = failedAttempts;
        this.blockedUntil = blockedUntil;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    Channel getChannel() {
        return channel;
    }

    String getSourceHash() {
        return sourceHash;
    }

    int getFailedAttempts() {
        return failedAttempts;
    }

    Instant getBlockedUntil() {
        return blockedUntil;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
