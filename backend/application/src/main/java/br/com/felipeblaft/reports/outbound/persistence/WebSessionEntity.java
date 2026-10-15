package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "web_session")
public class WebSessionEntity {

    @Id private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected WebSessionEntity() {}

    WebSessionEntity(
            UUID id,
            UUID teacherId,
            String tokenHash,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt) {
        this.id = id;
        this.teacherId = teacherId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.expiresAt = expiresAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTeacherId() {
        return teacherId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getLastUsedAt() {
        return lastUsedAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }
}
