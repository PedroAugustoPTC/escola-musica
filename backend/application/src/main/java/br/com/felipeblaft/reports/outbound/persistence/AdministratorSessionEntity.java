package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "administrator_session")
public class AdministratorSessionEntity {

    @Id private UUID id;

    @Column(name = "administrator_id", nullable = false)
    private UUID administratorId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason")
    private SessionRevocationReason revocationReason;

    protected AdministratorSessionEntity() {}

    AdministratorSessionEntity(
            UUID id,
            UUID administratorId,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt,
            Instant revokedAt,
            SessionRevocationReason revocationReason) {
        this.id = id;
        this.administratorId = administratorId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.revocationReason = revocationReason;
    }

    UUID getId() {
        return id;
    }

    UUID getAdministratorId() {
        return administratorId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    SessionRevocationReason getRevocationReason() {
        return revocationReason;
    }

    @Override
    public String toString() {
        return "AdministratorSessionEntity[id=" + id + "]";
    }
}
