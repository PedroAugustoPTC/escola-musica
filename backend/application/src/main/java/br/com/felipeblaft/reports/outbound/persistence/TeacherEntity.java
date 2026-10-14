package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teacher")
public class TeacherEntity {

    @Id private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "access_code_hash")
    private String accessCodeHash;

    @Column(name = "access_code_expires_at")
    private Instant accessCodeExpiresAt;

    @Column(name = "access_code_renewed_at")
    private Instant accessCodeRenewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TeacherEntity() {}

    TeacherEntity(
            UUID id,
            String name,
            String email,
            boolean active,
            String accessCodeHash,
            Instant accessCodeExpiresAt,
            Instant accessCodeRenewedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.active = active;
        this.accessCodeHash = accessCodeHash;
        this.accessCodeExpiresAt = accessCodeExpiresAt;
        this.accessCodeRenewedAt = accessCodeRenewedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getEmail() {
        return email;
    }

    boolean isActive() {
        return active;
    }

    String getAccessCodeHash() {
        return accessCodeHash;
    }

    Instant getAccessCodeExpiresAt() {
        return accessCodeExpiresAt;
    }

    Instant getAccessCodeRenewedAt() {
        return accessCodeRenewedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
