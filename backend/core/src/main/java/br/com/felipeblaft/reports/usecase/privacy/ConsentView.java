package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.privacy.ConsentCollectionMethod;
import br.com.felipeblaft.reports.domain.privacy.ConsentRelationship;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Consentimento como o administrador o vê, com a situação derivada. O {@code toString} não revela o
 * nome de quem consentiu.
 */
public record ConsentView(
        UUID id,
        ConsentStatus status,
        String consentingName,
        ConsentRelationship relationship,
        LocalDate consentedOn,
        int termVersion,
        ConsentCollectionMethod collectionMethod,
        Instant recordedAt,
        ActionOrigin recordedOrigin,
        LocalDate revokedOn) {

    static ConsentView of(GuardianConsent consent, ConsentStatus status) {
        return new ConsentView(
                consent.id(),
                status,
                consent.consentingName(),
                consent.relationship(),
                consent.consentedOn(),
                consent.termVersionNumber(),
                consent.collectionMethod(),
                consent.recordedAt(),
                consent.recordedOrigin(),
                consent.revokedOn());
    }

    @Override
    public String toString() {
        return "ConsentView";
    }
}
