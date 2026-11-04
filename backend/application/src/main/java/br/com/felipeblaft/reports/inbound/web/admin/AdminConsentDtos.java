package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.usecase.privacy.ConsentView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Corpos e respostas do consentimento do responsável (contrato {@code
 * api-privacy-operations.openapi.yaml}). Nenhuma regra de negócio: só conversão. O nome de quem
 * consentiu só sai nas telas do administrador e nunca vai a log nem a {@code toString}.
 */
public final class AdminConsentDtos {

    private AdminConsentDtos() {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ConsentSummary(
            java.util.UUID id,
            String status,
            String consentingName,
            String relationship,
            LocalDate consentedOn,
            int termVersion,
            String collectionMethod,
            Instant recordedAt,
            String recordedOrigin,
            LocalDate revokedOn) {

        @Override
        public String toString() {
            return "ConsentSummary[id=" + id + "]";
        }
    }

    public record RevocationRequest(String revokedOn, String revokedBy) {

        @Override
        public String toString() {
            return "RevocationRequest";
        }
    }

    public record TermRequest(String publishedOn, String text, Boolean requiresNewConsent) {

        @Override
        public String toString() {
            return "TermRequest";
        }
    }

    public record ConsentTerm(
            int number,
            LocalDate publishedOn,
            String text,
            boolean requiresNewConsent,
            boolean current) {

        @Override
        public String toString() {
            return "ConsentTerm[number=" + number + "]";
        }
    }

    public record PrivacyContact(String name, String contact) {

        @Override
        public String toString() {
            return "PrivacyContact";
        }
    }

    public record ConsentTermSummary(
            int number, LocalDate publishedOn, String text, PrivacyContact privacyContact) {

        @Override
        public String toString() {
            return "ConsentTermSummary[number=" + number + "]";
        }
    }

    public record PendingItem(java.util.UUID studentId, String studentName, String reason) {

        @Override
        public String toString() {
            return "PendingItem[studentId=" + studentId + "]";
        }
    }

    public record PendingPage(List<PendingItem> items, int page, int size, long total) {

        @Override
        public String toString() {
            return "PendingPage";
        }
    }

    static ConsentSummary summary(ConsentView view) {
        return new ConsentSummary(
                view.id(),
                view.status().name(),
                view.consentingName(),
                view.relationship().name(),
                view.consentedOn(),
                view.termVersion(),
                view.collectionMethod().name(),
                view.recordedAt(),
                view.recordedOrigin().name(),
                view.revokedOn());
    }

    static ConsentTerm term(ConsentTermVersion term, boolean current) {
        return new ConsentTerm(
                term.number(),
                term.publishedOn(),
                term.text(),
                term.requiresNewConsent(),
                current);
    }

    /** Termo vigente com o contato do responsável pela LGPD (público do formulário do professor). */
    public static ConsentTermSummary termSummary(ConsentTermVersion term, PrivacyOfficer officer) {
        return new ConsentTermSummary(
                term.number(),
                term.publishedOn(),
                term.text(),
                new PrivacyContact(officer.name(), officer.contact()));
    }

    /** Data do corpo; ausente ou inválida vira {@code VALIDATION_FAILED} no campo. */
    static LocalDate date(String text, String field) {
        try {
            return LocalDate.parse(text == null ? "" : text.strip());
        } catch (DateTimeParseException error) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of(
                            "fields",
                            List.of(Map.of("field", field, "code", ErrorCode.INVALID_DATA.name()))));
        }
    }
}
