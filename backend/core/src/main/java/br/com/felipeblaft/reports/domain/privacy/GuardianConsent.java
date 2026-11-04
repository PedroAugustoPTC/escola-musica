package br.com.felipeblaft.reports.domain.privacy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.PersonName;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Consentimento do responsável por um aluno menor (FR-030 a FR-033). Cada registro é novo; o
 * anterior fica como histórico. O nome de quem consentiu só deixa de existir na eliminação (FR-023)
 * e o {@code toString} nunca o revela.
 *
 * @param consentingName {@code null} só depois da eliminação do aluno
 * @param recordedByRole {@code null} na carga inicial
 * @param revokedBy papel de quem revogou ({@code MOTHER}, {@code FATHER}, {@code LEGAL_GUARDIAN},
 *     {@code OTHER} ou {@code STUDENT}), sem nome
 */
public record GuardianConsent(
        UUID id,
        UUID studentId,
        String consentingName,
        ConsentRelationship relationship,
        LocalDate consentedOn,
        UUID termVersionId,
        int termVersionNumber,
        ConsentCollectionMethod collectionMethod,
        UUID recordedById,
        Role recordedByRole,
        ActionOrigin recordedOrigin,
        Instant recordedAt,
        LocalDate revokedOn,
        String revokedBy,
        UUID revocationRecordedBy) {

    private static final Set<String> REVOKERS =
            Set.of("MOTHER", "FATHER", "LEGAL_GUARDIAN", "OTHER", "STUDENT");

    /** Valida a entrada contra os termos publicados; acumula as violações em {@code consent.*}. */
    public static GuardianConsent of(
            ConsentInput input,
            UUID studentId,
            List<ConsentTermVersion> terms,
            LocalDate today,
            Instant now,
            UUID recordedById,
            Role recordedByRole,
            ActionOrigin origin) {
        List<Map<String, String>> violations = new ArrayList<>();
        String name = null;
        try {
            name = PersonName.of(input.consentingName()).value();
        } catch (DomainException error) {
            violations.add(violation("consent.consentingName", error.code()));
        }
        ConsentRelationship relationship =
                parse(
                        ConsentRelationship.class,
                        input.relationship(),
                        "consent.relationship",
                        violations);
        LocalDate date = ConsentDates.parsePast(input.consentedOn(), today);
        if (date == null) {
            violations.add(violation("consent.consentedOn", ErrorCode.INVALID_DATA));
        }
        ConsentCollectionMethod method =
                parse(
                        ConsentCollectionMethod.class,
                        input.collectionMethod(),
                        "consent.collectionMethod",
                        violations);
        Optional<ConsentTermVersion> term = compatible(terms, input.termVersion());
        if (term.isEmpty()) {
            violations.add(violation("consent.termVersion", ErrorCode.INVALID_DATA));
        }
        if (!violations.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, Map.of("fields", violations));
        }
        return new GuardianConsent(
                UUID.randomUUID(),
                studentId,
                name,
                relationship,
                date,
                term.orElseThrow().id(),
                term.orElseThrow().number(),
                method,
                recordedById,
                recordedByRole,
                origin,
                now,
                null,
                null,
                null);
    }

    private static Optional<ConsentTermVersion> compatible(
            List<ConsentTermVersion> terms, Integer number) {
        if (number == null) {
            return Optional.empty();
        }
        int floor = ConsentTermVersion.compatibleFrom(terms);
        return terms.stream().filter(t -> t.number() == number && t.number() >= floor).findFirst();
    }

    private static <E extends Enum<E>> E parse(
            Class<E> type, String text, String field, List<Map<String, String>> violations) {
        if (text != null) {
            try {
                return Enum.valueOf(type, text.strip());
            } catch (IllegalArgumentException error) {
                violations.add(violation(field, ErrorCode.INVALID_DATA));
                return null;
            }
        }
        violations.add(violation(field, ErrorCode.INVALID_DATA));
        return null;
    }

    private static Map<String, String> violation(String field, ErrorCode code) {
        return Map.of("field", field, "code", code.name());
    }

    public boolean revoked() {
        return revokedOn != null;
    }

    /** Registra a revogação (FR-033); o registro continua no histórico. */
    public GuardianConsent revoke(LocalDate on, String by, UUID administratorId, LocalDate today) {
        if (revoked()) {
            throw new DomainException(ErrorCode.CONSENT_ALREADY_REVOKED);
        }
        List<Map<String, String>> violations = new ArrayList<>();
        if (on == null || on.isAfter(today) || on.isBefore(consentedOn)) {
            violations.add(violation("revokedOn", ErrorCode.INVALID_DATA));
        }
        if (by == null || !REVOKERS.contains(by)) {
            violations.add(violation("revokedBy", ErrorCode.INVALID_DATA));
        }
        if (!violations.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, Map.of("fields", violations));
        }
        return new GuardianConsent(
                id,
                studentId,
                consentingName,
                relationship,
                consentedOn,
                termVersionId,
                termVersionNumber,
                collectionMethod,
                recordedById,
                recordedByRole,
                recordedOrigin,
                recordedAt,
                on,
                by,
                administratorId);
    }

    /** Cópia sem o nome de quem consentiu (eliminação, FR-023). */
    public GuardianConsent withoutName() {
        return new GuardianConsent(
                id,
                studentId,
                null,
                relationship,
                consentedOn,
                termVersionId,
                termVersionNumber,
                collectionMethod,
                recordedById,
                recordedByRole,
                recordedOrigin,
                recordedAt,
                revokedOn,
                revokedBy,
                revocationRecordedBy);
    }

    @Override
    public String toString() {
        return "GuardianConsent";
    }
}
