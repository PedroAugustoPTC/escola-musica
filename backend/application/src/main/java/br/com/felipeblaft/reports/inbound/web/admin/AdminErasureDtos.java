package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureScope;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.usecase.privacy.GetErasureReceiptUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListErasureRequestsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.PreviewErasureUseCase;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corpos da eliminação a pedido (contrato {@code api-privacy-operations.openapi.yaml}). Nenhum
 * {@code toString} revela nome, token ou texto (Princípio III).
 */
final class AdminErasureDtos {

    private AdminErasureDtos() {}

    record RegisterRequest(
            String requesterRole,
            String requesterName,
            String requestedOn,
            String receivedVia,
            String protocolReference) {

        @Override
        public String toString() {
            return "RegisterRequest";
        }
    }

    record ConfirmRequest(String confirmationToken, String confirmationWord) {

        @Override
        public String toString() {
            return "ConfirmRequest";
        }
    }

    record Scope(
            Map<String, Integer> lessonReports,
            Map<String, Integer> semesterReports,
            int pdfs,
            int assignments,
            int consents) {}

    record Contact(String name, String contact) {

        @Override
        public String toString() {
            return "Contact";
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record RequestBody(
            UUID id,
            UUID studentId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String studentLabel,
            String status,
            String requesterRole,
            String requesterName,
            LocalDate requestedOn,
            String receivedVia,
            String protocolReference,
            Instant createdAt,
            Instant confirmedAt,
            Instant completedAt,
            Scope scope) {

        @Override
        public String toString() {
            return "RequestBody";
        }
    }

    record RequestPage(List<RequestBody> items, int page, int size, long total) {

        @Override
        public String toString() {
            return "RequestPage";
        }
    }

    record TeacherItem(UUID id, String name) {

        @Override
        public String toString() {
            return "TeacherItem";
        }
    }

    record PreviewBody(
            Scope scope,
            List<TeacherItem> teachers,
            String confirmationToken,
            Instant expiresAt,
            Contact privacyContact) {

        @Override
        public String toString() {
            return "PreviewBody";
        }
    }

    record ReceiptBody(
            UUID requestId,
            String studentShortId,
            String protocolReference,
            LocalDate requestedOn,
            Instant completedAt,
            Scope scope,
            Contact privacyContact) {

        @Override
        public String toString() {
            return "ReceiptBody";
        }
    }

    static Scope scope(ErasureScope scope) {
        return scope == null
                ? null
                : new Scope(
                        scope.lessonReports(),
                        scope.semesterReports(),
                        scope.pdfs(),
                        scope.assignments(),
                        scope.consents());
    }

    static Contact contact(PrivacyOfficer officer) {
        return new Contact(officer.name(), officer.contact());
    }

    static RequestBody request(ErasureRequest r, String studentLabel) {
        return new RequestBody(
                r.id(),
                r.studentId(),
                studentLabel,
                r.status().name(),
                r.requesterRole(),
                r.requesterName(),
                r.requestedOn(),
                r.receivedVia(),
                r.protocolReference(),
                r.createdAt(),
                r.confirmedAt(),
                r.completedAt(),
                scope(r.recordedScope()));
    }

    static RequestBody request(ListErasureRequestsUseCase.RequestView view) {
        return request(view.request(), view.studentLabel());
    }

    static PreviewBody preview(PreviewErasureUseCase.Preview p) {
        return new PreviewBody(
                scope(p.scope()),
                p.teachers().stream().map(t -> new TeacherItem(t.id(), t.name())).toList(),
                p.confirmationToken(),
                p.expiresAt(),
                contact(p.privacyContact()));
    }

    static ReceiptBody receipt(GetErasureReceiptUseCase.Receipt r) {
        return new ReceiptBody(
                r.requestId(),
                r.studentShortId(),
                r.protocolReference(),
                r.requestedOn(),
                r.completedAt(),
                scope(r.scope()),
                contact(r.privacyContact()));
    }
}
