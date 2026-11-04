package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.inbound.web.ConsentRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminConsentDtos.ConsentSummary;
import br.com.felipeblaft.reports.inbound.web.admin.AdminConsentDtos.ConsentTerm;
import br.com.felipeblaft.reports.inbound.web.admin.AdminConsentDtos.PendingItem;
import br.com.felipeblaft.reports.inbound.web.admin.AdminConsentDtos.PendingPage;
import br.com.felipeblaft.reports.inbound.web.admin.AdminConsentDtos.RevocationRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminConsentDtos.TermRequest;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.usecase.privacy.ListConsentTermsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListPendingConsentsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListStudentConsentsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.PublishConsentTermUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RecordConsentUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RevokeConsentUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consentimento do responsável na área administrativa (feature 007, US2). Adaptador fino: a
 * autorização, a validação e o evento da trilha são do núcleo. Toda resposta leva {@code
 * Cache-Control: no-store, private}, porque traz o nome de quem consentiu.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminConsentController {

    private static final String CORRELATION = AdminSessionFilter.CORRELATION_ATTRIBUTE;

    private final ListStudentConsentsUseCase listConsents;
    private final RecordConsentUseCase record;
    private final RevokeConsentUseCase revoke;
    private final ListPendingConsentsUseCase pending;
    private final ListConsentTermsUseCase listTerms;
    private final PublishConsentTermUseCase publish;

    public AdminConsentController(
            ListStudentConsentsUseCase listConsents,
            RecordConsentUseCase record,
            RevokeConsentUseCase revoke,
            ListPendingConsentsUseCase pending,
            ListConsentTermsUseCase listTerms,
            PublishConsentTermUseCase publish) {
        this.listConsents = listConsents;
        this.record = record;
        this.revoke = revoke;
        this.pending = pending;
        this.listTerms = listTerms;
        this.publish = publish;
    }

    @GetMapping("/students/{studentId}/consents")
    public ResponseEntity<List<ConsentSummary>> history(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId) {
        return noStore(
                ResponseEntity.ok(
                        listConsents.run(admin.actor(), studentId).stream()
                                .map(AdminConsentDtos::summary)
                                .toList()));
    }

    @PostMapping("/students/{studentId}/consents")
    public ResponseEntity<ConsentSummary> recordConsent(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId,
            @RequestBody ConsentRequest request,
            @RequestAttribute(CORRELATION) String correlationId) {
        var view =
                record.run(
                        admin.actor(),
                        new RecordConsentUseCase.Command(
                                studentId, ConsentRequest.input(request), correlationId));
        return noStore(ResponseEntity.status(HttpStatus.CREATED).body(AdminConsentDtos.summary(view)));
    }

    @PostMapping("/consents/{consentId}/revocation")
    public ResponseEntity<ConsentSummary> revokeConsent(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID consentId,
            @RequestBody RevocationRequest request,
            @RequestAttribute(CORRELATION) String correlationId) {
        var view =
                revoke.run(
                        admin.actor(),
                        new RevokeConsentUseCase.Command(
                                consentId,
                                AdminConsentDtos.date(request.revokedOn(), "revokedOn"),
                                request.revokedBy(),
                                correlationId));
        return noStore(ResponseEntity.ok(AdminConsentDtos.summary(view)));
    }

    @GetMapping("/consents/pending")
    public ResponseEntity<PendingPage> pending(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var found = pending.run(admin.actor(), PageRequest.of(page, size));
        return noStore(
                ResponseEntity.ok(
                        new PendingPage(
                                found.items().stream()
                                        .map(
                                                p ->
                                                        new PendingItem(
                                                                p.studentId(),
                                                                p.studentName(),
                                                                p.reason().name()))
                                        .toList(),
                                found.page(),
                                found.size(),
                                found.total())));
    }

    @GetMapping("/consent-terms")
    public ResponseEntity<List<ConsentTerm>> terms(
            @AuthenticationPrincipal AuthenticatedAdministrator admin) {
        List<ConsentTermVersion> all = listTerms.run(admin.actor());
        return noStore(
                ResponseEntity.ok(
                        all.stream()
                                .map(t -> AdminConsentDtos.term(t, t.number() == all.getFirst().number()))
                                .toList()));
    }

    @PostMapping("/consent-terms")
    public ResponseEntity<ConsentTerm> publishTerm(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestBody TermRequest request,
            @RequestAttribute(CORRELATION) String correlationId) {
        var term =
                publish.run(
                        admin.actor(),
                        new PublishConsentTermUseCase.Command(
                                AdminConsentDtos.date(request.publishedOn(), "publishedOn"),
                                request.text(),
                                Boolean.TRUE.equals(request.requiresNewConsent()),
                                correlationId));
        return noStore(
                ResponseEntity.status(HttpStatus.CREATED).body(AdminConsentDtos.term(term, true)));
    }

    private static <T> ResponseEntity<T> noStore(ResponseEntity<T> response) {
        return ResponseEntity.status(response.getStatusCode())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(response.getBody());
    }
}
