package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminAuditDtos.AuditEventPage;
import br.com.felipeblaft.reports.inbound.web.admin.AdminAuditDtos.AuditIntegrity;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.port.AuditTrailQueryPort;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.audit.QueryAuditTrailUseCase;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trilha de auditoria na área administrativa (feature 007, US1). Sem regra de negócio: a
 * autorização, a consulta e o registro da própria consulta são do núcleo. Toda resposta leva {@code
 * Cache-Control: no-store, private}.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-trail")
public class AdminAuditController {

    private static final String CORRELATION = AdminSessionFilter.CORRELATION_ATTRIBUTE;

    private final QueryAuditTrailUseCase query;
    private final VerifyAuditChainUseCase verify;
    private final AuditTrailQueryPort trail;
    private final MessageCatalog catalog;
    private final ClockPort clock;

    public AdminAuditController(
            QueryAuditTrailUseCase query,
            VerifyAuditChainUseCase verify,
            AuditTrailQueryPort trail,
            MessageCatalog catalog,
            ClockPort clock) {
        this.query = query;
        this.verify = verify;
        this.trail = trail;
        this.catalog = catalog;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<AuditEventPage> events(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String actorRole,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestAttribute(CORRELATION) String correlationId) {
        var found =
                query.run(
                        admin.actor(),
                        new QueryAuditTrailUseCase.Command(
                                date(from),
                                date(to),
                                actorId,
                                enumeration("actorRole", actorRole, Role::valueOf),
                                enumeration("origin", origin, ActionOrigin::valueOf),
                                operation,
                                enumeration("result", result, AuthorshipResult::valueOf),
                                targetId,
                                pageRequest(page, size),
                                correlationId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(AdminAuditDtos.page(found, catalog));
    }

    @GetMapping("/integrity")
    public ResponseEntity<AuditIntegrity> integrity(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false, defaultValue = "false") boolean verifyNow,
            @RequestAttribute(CORRELATION) String correlationId) {
        AuditIntegrity body;
        if (verifyNow) {
            var result = verify.run(admin.actor(), correlationId);
            body = AdminAuditDtos.integrity(result, clock.now());
        } else {
            body =
                    trail.lastVerification()
                            .map(
                                    last ->
                                            new AuditIntegrity(
                                                    last.intact() ? "INTACT" : "BROKEN",
                                                    last.verifiedAt(),
                                                    last.checkedEvents(),
                                                    null))
                            .orElse(new AuditIntegrity("NEVER_VERIFIED", null, 0, null));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }

    /** Data inválida vira ausente: o caso de uso recusa o período e grava a falha. */
    private static LocalDate date(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private static <T> T enumeration(String field, String text, Function<String, T> parse) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return parse.apply(text.strip());
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("fields", List.of(Map.of("field", field, "code", "INVALID_VALUE"))));
        }
    }

    private static PageRequest pageRequest(Integer page, Integer size) {
        return PageRequest.of(page, size);
    }
}
