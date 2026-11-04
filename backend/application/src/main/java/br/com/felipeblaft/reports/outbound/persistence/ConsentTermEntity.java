package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Linha de {@code consent_term_version} (V17): versão imutável do termo de consentimento. */
@Entity
@Table(name = "consent_term_version")
public class ConsentTermEntity {

    @Id private UUID id;

    @Column(name = "number", nullable = false)
    private int number;

    @Column(name = "published_on", nullable = false)
    private LocalDate publishedOn;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "requires_new_consent", nullable = false)
    private boolean requiresNewConsent;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConsentTermEntity() {}

    ConsentTermEntity(
            UUID id,
            int number,
            LocalDate publishedOn,
            String text,
            boolean requiresNewConsent,
            UUID createdBy,
            Instant createdAt) {
        this.id = id;
        this.number = number;
        this.publishedOn = publishedOn;
        this.text = text;
        this.requiresNewConsent = requiresNewConsent;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    int getNumber() {
        return number;
    }

    LocalDate getPublishedOn() {
        return publishedOn;
    }

    String getText() {
        return text;
    }

    boolean isRequiresNewConsent() {
        return requiresNewConsent;
    }

    UUID getCreatedBy() {
        return createdBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
