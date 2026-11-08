package br.com.felipeblaft.reports.domain.privacy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Versão do termo de consentimento (FR-032): imutável depois de publicada. Vigente é a de maior
 * número; compatível é qualquer versão de número igual ou maior que o da última versão que exige
 * novo consentimento.
 */
public record ConsentTermVersion(
        UUID id,
        int number,
        LocalDate publishedOn,
        String text,
        boolean requiresNewConsent,
        UUID createdBy,
        Instant createdAt) {

    public static final int TEXT_MAX = 20_000;

    /** Publica a próxima versão (número anterior + 1); acumula as violações. */
    public static ConsentTermVersion publish(
            Optional<ConsentTermVersion> current,
            LocalDate publishedOn,
            String text,
            boolean requiresNewConsent,
            UUID administratorId,
            LocalDate today,
            Instant now) {
        List<Map<String, String>> violations = new ArrayList<>();
        if (publishedOn == null || publishedOn.isAfter(today)) {
            violations.add(Map.of("field", "publishedOn", "code", ErrorCode.INVALID_DATA.name()));
        }
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty() || clean.length() > TEXT_MAX) {
            violations.add(Map.of("field", "text", "code", ErrorCode.INVALID_TEXT.name()));
        }
        if (!violations.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, Map.of("fields", violations));
        }
        int number = current.map(ConsentTermVersion::number).orElse(0) + 1;
        return new ConsentTermVersion(
                UUID.randomUUID(),
                number,
                publishedOn,
                clean,
                requiresNewConsent,
                administratorId,
                now);
    }

    /** Versão vigente: a de maior número. */
    public static Optional<ConsentTermVersion> current(List<ConsentTermVersion> terms) {
        return terms.stream().max(Comparator.comparingInt(ConsentTermVersion::number));
    }

    /** Menor número de versão ainda compatível (1 quando nenhuma exige novo consentimento). */
    public static int compatibleFrom(List<ConsentTermVersion> terms) {
        return terms.stream()
                .filter(ConsentTermVersion::requiresNewConsent)
                .mapToInt(ConsentTermVersion::number)
                .max()
                .orElse(1);
    }

    @Override
    public String toString() {
        return "ConsentTermVersion";
    }
}
