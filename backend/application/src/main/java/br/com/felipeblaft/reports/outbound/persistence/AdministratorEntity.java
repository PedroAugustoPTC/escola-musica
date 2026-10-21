package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "administrator")
public class AdministratorEntity {

    @Id private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "password_provisional", nullable = false)
    private boolean passwordProvisional;

    @Column(name = "password_provisional_expires_at")
    private Instant passwordProvisionalExpiresAt;

    @Column(name = "activation_code_hash")
    private String activationCodeHash;

    @Column(name = "activation_code_expires_at")
    private Instant activationCodeExpiresAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdministratorEntity() {}

    @SuppressWarnings("java:S107")
    AdministratorEntity(
            UUID id,
            String name,
            String email,
            boolean active,
            String passwordHash,
            boolean passwordProvisional,
            Instant passwordProvisionalExpiresAt,
            String activationCodeHash,
            Instant activationCodeExpiresAt,
            Instant activatedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.active = active;
        this.passwordHash = passwordHash;
        this.passwordProvisional = passwordProvisional;
        this.passwordProvisionalExpiresAt = passwordProvisionalExpiresAt;
        this.activationCodeHash = activationCodeHash;
        this.activationCodeExpiresAt = activationCodeExpiresAt;
        this.activatedAt = activatedAt;
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

    String getPasswordHash() {
        return passwordHash;
    }

    boolean isPasswordProvisional() {
        return passwordProvisional;
    }

    Instant getPasswordProvisionalExpiresAt() {
        return passwordProvisionalExpiresAt;
    }

    String getActivationCodeHash() {
        return activationCodeHash;
    }

    Instant getActivationCodeExpiresAt() {
        return activationCodeExpiresAt;
    }

    Instant getActivatedAt() {
        return activatedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "AdministratorEntity[id=" + id + "]";
    }
}
