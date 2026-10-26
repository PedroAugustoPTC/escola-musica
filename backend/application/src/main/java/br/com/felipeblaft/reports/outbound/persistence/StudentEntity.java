package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student")
public class StudentEntity {

    @Id private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "cpf_encrypted", nullable = false)
    private byte[] cpfEncrypted;

    @Column(name = "cpf_hash", nullable = false)
    private String cpfHash;

    @Column(name = "guardian_name")
    private String guardianName;

    @Column(name = "guardian_phone")
    private String guardianPhone;

    @Column(name = "guardian_email")
    private String guardianEmail;

    private String instrument;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentEntity() {}

    StudentEntity(
            UUID id,
            String name,
            LocalDate birthDate,
            byte[] cpfEncrypted,
            String cpfHash,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String instrument,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.birthDate = birthDate;
        this.cpfEncrypted = cpfEncrypted;
        this.cpfHash = cpfHash;
        this.guardianName = guardianName;
        this.guardianPhone = guardianPhone;
        this.guardianEmail = guardianEmail;
        this.instrument = instrument;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    LocalDate getBirthDate() {
        return birthDate;
    }

    byte[] getCpfEncrypted() {
        return cpfEncrypted;
    }

    String getCpfHash() {
        return cpfHash;
    }

    String getGuardianName() {
        return guardianName;
    }

    String getGuardianPhone() {
        return guardianPhone;
    }

    String getGuardianEmail() {
        return guardianEmail;
    }

    String getInstrument() {
        return instrument;
    }

    boolean isActive() {
        return active;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
