package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha de {@code student_consent} (V17). Cada registro é novo; a única alteração é a revogação,
 * que preenche as três colunas {@code revoked_*}. O nome de quem consentiu só fica nulo na
 * eliminação.
 */
@Entity
@Table(name = "student_consent")
public class StudentConsentEntity {

    @Id private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "consenting_name")
    private String consentingName;

    @Column(name = "relationship", nullable = false)
    private String relationship;

    @Column(name = "consented_on", nullable = false)
    private LocalDate consentedOn;

    @Column(name = "term_version_id", nullable = false)
    private UUID termVersionId;

    @Column(name = "collection_method", nullable = false)
    private String collectionMethod;

    @Column(name = "recorded_by_id")
    private UUID recordedById;

    @Column(name = "recorded_by_role")
    private String recordedByRole;

    @Column(name = "recorded_origin", nullable = false)
    private String recordedOrigin;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "revoked_on")
    private LocalDate revokedOn;

    @Column(name = "revoked_by_role")
    private String revokedByRole;

    @Column(name = "revocation_recorded_by")
    private UUID revocationRecordedBy;

    protected StudentConsentEntity() {}

    @SuppressWarnings("java:S107")
    StudentConsentEntity(
            UUID id,
            UUID studentId,
            String consentingName,
            String relationship,
            LocalDate consentedOn,
            UUID termVersionId,
            String collectionMethod,
            UUID recordedById,
            String recordedByRole,
            String recordedOrigin,
            Instant recordedAt,
            LocalDate revokedOn,
            String revokedByRole,
            UUID revocationRecordedBy) {
        this.id = id;
        this.studentId = studentId;
        this.consentingName = consentingName;
        this.relationship = relationship;
        this.consentedOn = consentedOn;
        this.termVersionId = termVersionId;
        this.collectionMethod = collectionMethod;
        this.recordedById = recordedById;
        this.recordedByRole = recordedByRole;
        this.recordedOrigin = recordedOrigin;
        this.recordedAt = recordedAt;
        this.revokedOn = revokedOn;
        this.revokedByRole = revokedByRole;
        this.revocationRecordedBy = revocationRecordedBy;
    }

    UUID getId() {
        return id;
    }

    UUID getStudentId() {
        return studentId;
    }

    String getConsentingName() {
        return consentingName;
    }

    String getRelationship() {
        return relationship;
    }

    LocalDate getConsentedOn() {
        return consentedOn;
    }

    UUID getTermVersionId() {
        return termVersionId;
    }

    String getCollectionMethod() {
        return collectionMethod;
    }

    UUID getRecordedById() {
        return recordedById;
    }

    String getRecordedByRole() {
        return recordedByRole;
    }

    String getRecordedOrigin() {
        return recordedOrigin;
    }

    Instant getRecordedAt() {
        return recordedAt;
    }

    LocalDate getRevokedOn() {
        return revokedOn;
    }

    String getRevokedByRole() {
        return revokedByRole;
    }

    UUID getRevocationRecordedBy() {
        return revocationRecordedBy;
    }
}
